#!/usr/bin/env python3
"""Checkout races on a disposable postgres:17 container with network=none only.

Run: python3 concurrency-extra.py CONTAINER_NAME
No host/remote database connection is accepted. Only newly generated fixture
UUIDs and previously unused product IDs above 1,000,000 are modified/deleted.
"""
import concurrent.futures
import json
import queue
import secrets
import subprocess
import sys
import threading
import time
import uuid


class TestFailure(RuntimeError):
    pass


def sql_literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def ensure(condition, message):
    if not condition:
        raise TestFailure(message)


class Database:
    def __init__(self, container):
        self.container = container
        check = subprocess.run(['docker', 'inspect', container], capture_output=True, text=True, check=True)
        info = json.loads(check.stdout)[0]
        ensure(info['Config']['Image'] == 'postgres:17', 'refusing: image must be exactly postgres:17')
        ensure(info['HostConfig']['NetworkMode'] == 'none', 'refusing: container network must be none')
        ensure(info['State']['Running'], 'refusing: test container is not running')
        ensure(not info['HostConfig'].get('PortBindings'), 'refusing: test container publishes ports')
        self.prefix = 'rpc_race_' + uuid.uuid4().hex[:14]

    def command(self):
        return ['docker', 'exec', '-i', self.container, 'psql', '-U', 'postgres', '-d', 'postgres',
                '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=sqlstate']

    def run(self, sql, expected_success=True):
        result = subprocess.run(self.command(), input=sql, capture_output=True, text=True, timeout=22)
        if expected_success and result.returncode:
            raise TestFailure('local SQL failed: ' + result.stderr.strip())
        return result

    def scalar(self, sql):
        result = self.run(sql)
        values = [line.strip() for line in result.stdout.splitlines() if line.strip()]
        ensure(bool(values), 'scalar query returned no value')
        return values[-1]

    def wait_locked(self, names, kind=None, timeout=8):
        target = ','.join(sql_literal(n) for n in names)
        filter_sql = '' if kind is None else ' and wait_event=' + sql_literal(kind)
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            count = int(self.scalar("select count(*) from pg_stat_activity where application_name in ("
                                    + target + ") and wait_event_type='Lock'" + filter_sql + ';'))
            if count == len(names):
                return
            time.sleep(0.04)
        raise TestFailure('timed out waiting for the intended database lock barrier')


class Gate:
    """Persistent transaction released by an explicit command, never a timed sleep."""
    def __init__(self, db, suffix):
        self.db = db
        self.process = subprocess.Popen(db.command(), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE, text=True, bufsize=1)
        self.lines = queue.Queue()
        self.errors = []
        def read_stdout():
            for line in self.process.stdout:
                self.lines.put(line.rstrip('\n'))
            self.lines.put(None)
        def read_stderr():
            self.errors.extend(self.process.stderr.readlines())
        self.reader = threading.Thread(target=read_stdout, daemon=True)
        self.error_reader = threading.Thread(target=read_stderr, daemon=True)
        self.reader.start(); self.error_reader.start()
        self.send('set application_name=' + sql_literal(db.prefix + '_gate_' + suffix) + ";\n"
                  "set statement_timeout='15s';\nset idle_in_transaction_session_timeout='20s';")

    def send(self, sql, timeout=10):
        marker = 'GATE_DONE_' + uuid.uuid4().hex
        self.process.stdin.write(sql.rstrip() + '\n\\echo ' + marker + '\n')
        self.process.stdin.flush()
        deadline = time.monotonic() + timeout
        result = []
        while time.monotonic() < deadline:
            try:
                value = self.lines.get(timeout=max(0.01, deadline - time.monotonic()))
            except queue.Empty:
                break
            if value == marker:
                return result
            if value is None:
                raise TestFailure('gate transaction failed: ' + ''.join(self.errors).strip())
            if value.strip():
                result.append(value.strip())
        raise TestFailure('gate transaction did not finish its command')

    def __enter__(self):
        return self

    def __exit__(self, *_):
        try:
            if self.process.poll() is None:
                self.send('rollback;', timeout=3)
                self.process.stdin.close()
                self.process.wait(timeout=3)
        except (BrokenPipeError, TestFailure, subprocess.TimeoutExpired):
            self.process.kill()
            self.process.wait(timeout=3)


def actor_sql(user):
    claims = json.dumps({'sub': user, 'role': 'authenticated'})
    return ('set local role authenticated;\nset local request.jwt.claim.sub=' + sql_literal(user)
            + ';\nset local request.jwt.claims=' + sql_literal(claims) + ';\n')


def checkout(db, name, user, request, product):
    payload = json.dumps([{'product_id': product, 'quantity': 1}])
    sql = ('set application_name=' + sql_literal(name) + ";\nbegin;\nset local statement_timeout='15s';\n"
           + actor_sql(user) + "select 'ORDER:'||public.checkout_cart(" + sql_literal(request)
           + ',' + sql_literal(payload) + '::jsonb,null)::text;\ncommit;')
    result = db.run(sql, expected_success=False)
    order_ids = [int(line[6:]) for line in result.stdout.splitlines() if line.startswith('ORDER:')]
    return {'ok': result.returncode == 0, 'orders': order_ids, 'error': result.stderr}


def account_gate(gate, user):
    gate.send('begin; select pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('
              + sql_literal(user) + '::text,0));')


def state(db, user, product):
    value = db.scalar("select json_build_object('orders',(select count(*) from public.orders where user_id="
                      + sql_literal(user) + "::uuid),'cart',(select count(*) from public.cart_items where user_id="
                      + sql_literal(user) + "::uuid),'requests',(select count(*) from private.checkout_requests where user_id="
                      + sql_literal(user) + "::uuid),'stock',(select stock from public.products where id="
                      + str(product) + '));')
    return json.loads(value)


def main(container):
    db = Database(container)
    users = {name: str(uuid.uuid4()) for name in ('same', 'different', 'blocked', 'newrow', 'admin')}
    user_list = ','.join(sql_literal(u) + '::uuid' for u in users.values())
    base = None
    for _ in range(10):
        candidate = 1500000000 + secrets.randbelow(50000000)
        count = int(db.scalar('select count(*) from public.products where id between '
                              + str(candidate) + ' and ' + str(candidate + 4) + ';'))
        if count == 0:
            base = candidate
            break
    ensure(base is not None, 'could not reserve unused fixture product IDs')
    products = {name: base + i for i, name in enumerate(('same', 'different', 'blocked', 'newrow', 'added'))}
    product_list = ','.join(str(p) for p in products.values())
    created = False
    try:
        statements = ['begin;']
        for name, user in users.items():
            email = 'rpc-concurrency+' + user + '@example.test'
            statements.append('insert into auth.users(id,email) values(' + sql_literal(user)
                              + '::uuid,' + sql_literal(email) + ');')
            statements.append('insert into public.profiles(id,email,name,is_admin) values('
                              + sql_literal(user) + '::uuid,' + sql_literal(email)
                              + ",'RPC concurrency fixture'," + ('true' if name == 'admin' else 'false') + ');')
        for name, product in products.items():
            statements.append('insert into public.products(id,name,price,stock) values('
                              + str(product) + ',' + sql_literal('RPC race ' + name) + ',100,10);')
        for name in ('same', 'different', 'blocked', 'newrow'):
            statements.append('insert into public.cart_items(user_id,product_id,product_name,price,quantity) values('
                              + sql_literal(users[name]) + '::uuid,' + str(products[name])
                              + ",'RPC race untrusted snapshot',0.01,1);")
        statements.append('commit;')
        db.run('\n'.join(statements)); created = True

        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            request = str(uuid.uuid4())
            names = [db.prefix + '_same_a', db.prefix + '_same_b']
            with Gate(db, 'same') as gate:
                account_gate(gate, users['same'])
                jobs = [pool.submit(checkout, db, n, users['same'], request, products['same']) for n in names]
                db.wait_locked(names, 'advisory')
                gate.send('commit;')
                results = [j.result(timeout=18) for j in jobs]
            ensure(all(r['ok'] for r in results), 'same-key concurrent retry unexpectedly failed')
            ensure(all(len(r['orders']) == 1 for r in results), 'same-key retry did not return one order ID each')
            ensure(results[0]['orders'] == results[1]['orders'], 'same-key retry returned different orders')
            ensure(state(db, users['same'], products['same']) == {'orders':1,'cart':0,'requests':1,'stock':9},
                   'same-key retry duplicated order, stock decrement, or request record')
            print('PASS same account / same request UUID: one order and one stock decrement', flush=True)

            names = [db.prefix + '_different_a', db.prefix + '_different_b']
            with Gate(db, 'different') as gate:
                account_gate(gate, users['different'])
                jobs = [pool.submit(checkout, db, n, users['different'], str(uuid.uuid4()), products['different']) for n in names]
                db.wait_locked(names, 'advisory')
                gate.send('commit;')
                results = [j.result(timeout=18) for j in jobs]
            ensure(sum(r['ok'] for r in results) == 1, 'different-key concurrent checkouts must have one success')
            ensure('40001' in next(r['error'] for r in results if not r['ok']), 'losing different-key checkout must report cart changed')
            ensure(state(db, users['different'], products['different']) == {'orders':1,'cart':0,'requests':1,'stock':9},
                   'different request UUIDs purchased the same cart twice')
            print('PASS same account / different request UUIDs: one success, one cart-changed rejection', flush=True)

            name = db.prefix + '_blocked_checkout'
            with Gate(db, 'blocked') as gate:
                account_gate(gate, users['blocked'])
                job = pool.submit(checkout, db, name, users['blocked'], str(uuid.uuid4()), products['blocked'])
                db.wait_locked([name], 'advisory')
                # The block uses the actual admin RPC within the transaction
                # already holding the target account lock (reentrant acquisition).
                gate.send(actor_sql(users['admin']) + 'select public.set_user_blocked('
                          + sql_literal(users['blocked']) + '::uuid,true);\ncommit;')
                result = job.result(timeout=18)
            ensure(not result['ok'] and '42501' in result['error'], 'checkout resumed after a committed block without reauthorization')
            ensure(state(db, users['blocked'], products['blocked']) == {'orders':0,'cart':1,'requests':0,'stock':10},
                   'blocked checkout changed cart, orders or stock')
            ensure(db.scalar('select (is_blocked and blocked)::text from public.profiles where id='
                             + sql_literal(users['blocked']) + '::uuid;') == 'true', 'block RPC did not commit both block flags')
            print('PASS block committed while checkout waited: checkout denied after acquiring account lock', flush=True)

            name = db.prefix + '_newrow_checkout'
            with Gate(db, 'newrow') as gate:
                # The checkout reaches inventory only after capturing/locking its
                # original cart rows. A product lock is the snapshot barrier.
                gate.send('begin; select id from public.products where id=' + str(products['newrow']) + ' for update;')
                job = pool.submit(checkout, db, name, users['newrow'], str(uuid.uuid4()), products['newrow'])
                db.wait_locked([name])
                # A distinct product avoids its FK key-share lock conflicting
                # with the deliberately locked old product row.
                db.run('begin;\n' + actor_sql(users['newrow'])
                       + 'insert into public.cart_items(user_id,product_id,product_name,price,quantity) values('
                       + sql_literal(users['newrow']) + '::uuid,' + str(products['added'])
                       + ",'RPC added during checkout',0.01,2);\ncommit;")
                gate.send('commit;')
                result = job.result(timeout=18)
            ensure(result['ok'], 'checkout failed when a separate cart item was inserted after snapshot')
            ensure(state(db, users['newrow'], products['newrow']) == {'orders':1,'cart':1,'requests':1,'stock':9},
                   'checkout erased newly inserted cart row or duplicated stock change')
            ensure(int(db.scalar('select count(*) from public.cart_items where user_id=' + sql_literal(users['newrow'])
                                 + '::uuid and product_id=' + str(products['added']) + ' and quantity=2;')) == 1,
                   'new cart row was not preserved with its quantity')
            ensure(int(db.scalar('select stock from public.products where id=' + str(products['added']) + ';')) == 10,
                   'new cart item was incorrectly purchased')
            print('PASS new cart row inserted after snapshot: row preserved and not purchased', flush=True)
        print('ALL 4 extra concurrency cases passed', flush=True)
    finally:
        if created:
            # Match only connections and rows uniquely created by this script.
            db.run('select pg_terminate_backend(pid) from pg_stat_activity where pid<>pg_backend_pid() and application_name like '
                   + sql_literal(db.prefix + '%') + ';', expected_success=False)
            db.run('begin;\nset local statement_timeout=\'15s\';\n'
                   + 'delete from private.checkout_requests where user_id in (' + user_list + ');\n'
                   + 'delete from public.order_items where order_id in (select id from public.orders where user_id in (' + user_list + '));\n'
                   + 'delete from public.orders where user_id in (' + user_list + ');\n'
                   + 'delete from public.cart_items where user_id in (' + user_list + ');\n'
                   + 'delete from public.profiles where id in (' + user_list + ');\n'
                   + 'delete from auth.users where id in (' + user_list + ');\n'
                   + 'delete from public.products where id in (' + product_list + ');\ncommit;')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: concurrency-extra.py DISPOSABLE_POSTGRES17_NETWORK_NONE_CONTAINER')
    try:
        main(sys.argv[1])
    except (TestFailure, subprocess.SubprocessError) as error:
        print('FAIL:', error, file=sys.stderr)
        raise SystemExit(1)
