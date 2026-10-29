-- LOCAL, disposable restored PostgreSQL only. Never run this fixture on production.
-- Existing rows are not deleted/changed. UUIDs are random, explicit fixture IDs >1e9.
-- BEGIN/ROLLBACK restores table data; checkout can advance sequences, hence LOCAL ONLY.
-- Run after the reviewed security-and-checkout migration, psql ON_ERROR_STOP=1.
begin;
set local search_path = public, extensions;

create temporary table rpc_test_people(role_name text primary key,id uuid not null,email text not null) on commit drop;
insert into rpc_test_people
select role_name,gen_random_uuid(),role_name || '+' || gen_random_uuid()::text || '@example.test'
from (values ('a'),('b'),('admin'),('manager'),('signup')) r(role_name);
grant select on rpc_test_people to anon,authenticated;
create temporary table rpc_test_meta(key text primary key,value text not null) on commit drop;
insert into rpc_test_meta values ('promo_code',upper('RPC_VALID_'||gen_random_uuid()::text)),('request_id',gen_random_uuid()::text);
grant select on rpc_test_meta to anon,authenticated;
create temporary table rpc_test_products(kind text primary key,id integer not null,available boolean not null) on commit drop;
insert into rpc_test_products values ('good',2000000001,true),('nan',2000000002,false),('null_stock',2000000003,false);
grant select on rpc_test_products to anon,authenticated;

create function pg_temp.assert_true(p_test boolean,p_message text) returns void
language plpgsql security invoker as $$ begin
 if p_test is distinct from true then raise exception 'Regression failed: %',p_message;end if;
end $$;
create function pg_temp.expect_error(p_sql text,p_states text[],p_message text) returns void
language plpgsql security invoker as $$
declare v_failed boolean:=false;begin
 begin execute p_sql;
 exception when others then
  if not sqlstate=any(p_states) then raise exception 'Regression %: unexpected SQLSTATE % (%)',p_message,sqlstate,sqlerrm;end if;
  v_failed:=true;
 end;
 if not v_failed then raise exception 'Regression failed: operation succeeded (%).',p_message;end if;
end $$;
do $$begin execute format('grant usage on schema %I to anon,authenticated',pg_my_temp_schema()::regnamespace::text);end$$;

select pg_temp.assert_true(not exists(select 1 from public.products where id between 2000000001 and 2000000003),'fixture product IDs are unused');
select pg_temp.assert_true(not exists(select 1 from public.orders where id=2000000010),'fixture order ID is unused');
insert into auth.users(id,email) select id,email from rpc_test_people;
insert into public.profiles(id,email,name,is_admin,is_manager)
 select id,email,'RPC regression',role_name='admin',role_name='manager' from rpc_test_people where role_name<>'signup';
insert into public.products(id,name,price,stock) values(2000000001,'RPC genuine snapshot',100.05,10);
insert into public.promo_codes(id,code,discount_percent,expiry_date,max_uses,used_count,is_active)
 select 2000000011,value,10,current_date+1,1,0,true from rpc_test_meta where key='promo_code';
insert into public.cart_items(id,user_id,product_id,product_name,price,quantity)
 select 2000000020+row_number() over(order by role_name),id,2000000001,'Untrusted client snapshot',0.01,1 from rpc_test_people where role_name in ('a','b');
insert into public.orders(id,user_id,total_amount,discount_amount,final_amount,status)
 select 2000000010,id,200,0,200,'pending' from rpc_test_people where role_name='b';
insert into public.order_items(id,order_id,product_id,product_name,price,quantity,subtotal)
 values(2000000010,2000000010,2000000001,'RPC previous order',100,2,200);

-- Bad source values must be stopped either by table constraints or checkout.
do $$begin
 begin
  insert into public.products(id,name,price,stock) values(2000000002,'RPC NaN source','NaN'::numeric,1);
  update rpc_test_products set available=true where kind='nan';
 exception when check_violation or not_null_violation then null;end;
 begin
  insert into public.products(id,name,price,stock) values(2000000003,'RPC null stock source',100,null);
  update rpc_test_products set available=true where kind='null_stock';
 exception when check_violation or not_null_violation then null;end;
end$$;
select pg_temp.expect_error($q$insert into public.products(id,name,price,stock) values(2000000004,'RPC null price',null,1)$q$,array['23502','23514'],'null product price is prohibited');
select pg_temp.expect_error($q$insert into public.promo_codes(code,discount_percent,expiry_date,max_uses,used_count,is_active) values('RPC_NULL_ACTIVE',10,current_date+1,1,0,null)$q$,array['23502','23514'],'nullable promo is_active is prohibited');
select pg_temp.expect_error($q$insert into public.promo_codes(code,discount_percent,expiry_date,max_uses,used_count,is_active) values('RPC_NULL_EXPIRY',10,null,1,0,true)$q$,array['23502','23514'],'nullable promo expiry is prohibited');
select pg_temp.expect_error($q$insert into public.promo_codes(code,discount_percent,expiry_date,max_uses,used_count,is_active) values('RPC_NAN_DISCOUNT','NaN'::numeric,current_date+1,1,0,true)$q$,array['23514'],'NaN promo discount is prohibited');

set local role anon;
select set_config('request.jwt.claim.sub','',true);
select pg_temp.assert_true((select count(*) from public.products where id=2000000001)=1,'guest can view catalog');
select pg_temp.expect_error('select * from public.profiles',array['42501'],'guest cannot read profiles');
select pg_temp.expect_error('select * from public.cart_items',array['42501'],'guest cannot read carts');
select pg_temp.expect_error('select * from public.orders',array['42501'],'guest cannot read orders');
select pg_temp.expect_error('select * from public.order_items',array['42501'],'guest cannot read order items');
select pg_temp.expect_error('select * from public.promo_codes',array['42501'],'guest cannot read promo codes');
select pg_temp.expect_error('select * from public.login_attempts',array['42501'],'guest cannot read login attempts');
select pg_temp.assert_true(not has_function_privilege(current_user,'public.checkout_cart(uuid,jsonb,text)','EXECUTE'),'guest cannot execute checkout');
select pg_temp.assert_true(not has_function_privilege(current_user,'public.set_user_blocked(uuid,boolean)','EXECUTE'),'guest cannot execute account blocking');

set local role authenticated;
select set_config('request.jwt.claim.sub',(select id::text from rpc_test_people where role_name='signup'),true);
select pg_temp.expect_error(format('insert into public.profiles(id,email,is_admin) values(%L,%L,true)',(select id from rpc_test_people where role_name='signup'),(select email from rpc_test_people where role_name='signup')),array['42501'],'self-signup cannot set admin flag');
select pg_temp.expect_error(format('insert into public.profiles(id,email) values(%L,%L)',(select id from rpc_test_people where role_name='signup'),(select email from rpc_test_people where role_name='admin')),array['42501','23514'],'profile email must match trusted Auth email');
insert into public.profiles(id,email,name) select id,email,'Self signup fixture' from rpc_test_people where role_name='signup';
select pg_temp.assert_true((select count(*) from public.profiles where id=auth.uid() and not coalesce(is_admin,false) and not coalesce(is_manager,false))=1,'valid signup creates only a regular profile');
select pg_temp.expect_error(format('update public.profiles set email=%L where id=auth.uid()',(select email from rpc_test_people where role_name='admin')),array['42501'],'profile edit cannot change Auth email');

-- Forged user metadata never grants staff permissions.
select set_config('request.jwt.claim.sub',(select id::text from rpc_test_people where role_name='a'),true);
select set_config('request.jwt.claims',jsonb_build_object('sub',(select id from rpc_test_people where role_name='a'),'role','authenticated','user_metadata',jsonb_build_object('is_admin',true,'is_manager',true))::text,true);
select pg_temp.assert_true((select count(*) from public.profiles)=1,'customer sees only own profile');
select pg_temp.assert_true((select count(*) from public.cart_items)=1,'customer sees only own cart');
select pg_temp.assert_true((select count(*) from public.orders)=0,'customer cannot see another order');
select pg_temp.assert_true((select count(*) from public.order_items)=0,'customer cannot see another order item');
select pg_temp.expect_error('select * from private.checkout_requests',array['42501'],'idempotency records are private');
select pg_temp.expect_error('update public.profiles set is_admin=true where id=auth.uid()',array['42501'],'profile role escalation is denied');
select pg_temp.expect_error(format('insert into public.cart_items(user_id,product_id,product_name,price,quantity) values(%L,2000000001,''foreign'',1,1)',(select id from rpc_test_people where role_name='b')),array['42501'],'cannot insert into another cart');
do $$declare affected integer;begin
 update public.profiles set name='RPC stolen name' where id=(select id from rpc_test_people where role_name='b');get diagnostics affected=row_count;
 perform pg_temp.assert_true(affected=0,'cannot update another profile');
 update public.cart_items set quantity=2 where user_id=(select id from rpc_test_people where role_name='b');get diagnostics affected=row_count;
 perform pg_temp.assert_true(affected=0,'cannot update another cart');
 update public.products set price=0.01 where id=2000000001;get diagnostics affected=row_count;
 perform pg_temp.assert_true(affected=0,'forged metadata cannot change catalog prices');
 update public.orders set status='completed' where id=2000000010;get diagnostics affected=row_count;
 perform pg_temp.assert_true(affected=0,'customer cannot change order status');
end$$;
select pg_temp.expect_error($q$insert into public.products(name,price,stock) values('RPC customer forged product',1,1)$q$,array['42501'],'customer cannot create product');
select pg_temp.expect_error($q$insert into public.promo_codes(code,discount_percent,expiry_date,max_uses,used_count,is_active) values('RPC_USER_FORGED_PROMO',99,current_date+1,1,0,true)$q$,array['42501'],'customer cannot create a promo');
select pg_temp.expect_error(format('select public.set_user_blocked(%L,true)',(select id from rpc_test_people where role_name='b')),array['42501'],'customer cannot block accounts');
select pg_temp.assert_true(not exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname in ('add_product','increment_promo_code_usage','deactivate_promo_code','check_block_profile','check_block_user','check_block_profile_by_email') and has_function_privilege(current_user,p.oid,'EXECUTE')),'legacy RPCs cannot bypass new permissions');

-- Invalid payloads must fail before creating orders or consuming cart/stock.
do $$declare payload jsonb;begin
 for payload in select value from jsonb_array_elements('[null,{},[],[null],[true],[1],[{"quantity":1}],[{"product_id":2000000001}],[{"product_id":2000000001,"quantity":null}],[{"product_id":2000000001,"quantity":0}],[{"product_id":2000000001,"quantity":-1}],[{"product_id":2000000001,"quantity":1.5}],[{"product_id":2000000001.5,"quantity":1}],[{"product_id":2000000001,"quantity":"1"}],[{"product_id":2000000001,"quantity":true}],[{"product_id":2000000001,"quantity":100000000000000000000}],[{"product_id":2000000001,"quantity":1},{"product_id":2000000001,"quantity":1}]]'::jsonb) loop
  perform pg_temp.expect_error(format('select public.checkout_cart(%L,%L::jsonb,null)',gen_random_uuid(),payload::text),array['P0001','22023','22P02','22003','23514'],'malformed checkout payload rejected');
 end loop;
 perform pg_temp.assert_true((select count(*) from public.orders)=0,'invalid payload leaves no order');
 perform pg_temp.assert_true((select count(*) from public.cart_items)=1,'invalid payload leaves cart intact');
end$$;

select public.checkout_cart((select value::uuid from rpc_test_meta where key='request_id'),'[{"product_id":2000000001,"quantity":1,"price":0.01,"product_name":"forged"}]'::jsonb,(select '  '||lower(value)||'  ' from rpc_test_meta where key='promo_code')) as created_order;
select public.checkout_cart((select value::uuid from rpc_test_meta where key='request_id'),'[{"product_id":2000000001,"quantity":1}]'::jsonb,(select value from rpc_test_meta where key='promo_code')) as same_order;
select pg_temp.assert_true((select count(*) from public.orders)=1,'retry returns one order');
select pg_temp.assert_true((select count(*) from public.orders where total_amount=100.05 and discount_amount=10.01 and final_amount=90.04)=1,'prices and rounded discount come from server');
select pg_temp.assert_true((select count(*) from public.order_items where product_name='RPC genuine snapshot' and price=100.05 and quantity=1 and subtotal=100.05)=1,'order stores authoritative product snapshot');
select pg_temp.assert_true((select count(*) from public.cart_items)=0,'purchased cart is empty');
select pg_temp.expect_error(format('select public.checkout_cart(%L,''[{"product_id":2000000001,"quantity":2}]''::jsonb,%L)',(select value from rpc_test_meta where key='request_id'),(select value from rpc_test_meta where key='promo_code')),array['P0001','22023'],'idempotency key cannot be rebound to changed payload');

-- Staff can manage catalog/order status but cannot acquire admin powers or carts.
select set_config('request.jwt.claim.sub',(select id::text from rpc_test_people where role_name='manager'),true);
select set_config('request.jwt.claims',jsonb_build_object('sub',(select id from rpc_test_people where role_name='manager'),'role','authenticated')::text,true);
select pg_temp.assert_true((select count(*) from public.profiles where id in (select id from rpc_test_people))=5,'manager can read fixture profiles');
select pg_temp.assert_true((select count(*) from public.orders where user_id in (select id from rpc_test_people))=2,'manager can read fixture orders');
select pg_temp.assert_true((select count(*) from public.cart_items)=0,'manager cannot see customer carts');
do $$declare affected integer;begin
 update public.products set description='RPC manager edit' where id=2000000001;get diagnostics affected=row_count;
 perform pg_temp.assert_true(affected=1,'manager can edit catalog');
 update public.orders set status='completed' where id=2000000010;get diagnostics affected=row_count;
 perform pg_temp.assert_true(affected=1,'manager can update order status');
 update public.promo_codes set discount_percent=50 where id=2000000011;get diagnostics affected=row_count;
 perform pg_temp.assert_true(affected=0,'manager cannot edit promo codes');
end$$;
select pg_temp.expect_error(format('select public.set_user_blocked(%L,true)',(select id from rpc_test_people where role_name='b')),array['42501'],'manager cannot block accounts');
select pg_temp.expect_error('select * from public.login_attempts',array['42501'],'manager cannot read private login attempts');

select set_config('request.jwt.claim.sub',(select id::text from rpc_test_people where role_name='admin'),true);
select public.set_user_blocked((select id from rpc_test_people where role_name='b'),true);
select pg_temp.assert_true((select is_blocked and blocked from public.profiles where id=(select id from rpc_test_people where role_name='b')),'admin blocking keeps both historical flags in sync');
select pg_temp.expect_error('select public.set_user_blocked(auth.uid(),true)',array['P0001','22023'],'administrator cannot lock own account');
select set_config('request.jwt.claim.sub',(select id::text from rpc_test_people where role_name='b'),true);
select pg_temp.assert_true((select count(*) from public.cart_items)=0,'blocked user loses cart access immediately');
select pg_temp.assert_true((select count(*) from public.orders)=0,'blocked user loses order access immediately');
select pg_temp.expect_error(format('select public.checkout_cart(%L,''[{"product_id":2000000001,"quantity":1}]''::jsonb,null)',gen_random_uuid()),array['42501'],'blocked JWT cannot create order');
select set_config('request.jwt.claim.sub',(select id::text from rpc_test_people where role_name='admin'),true);
select public.set_user_blocked((select id from rpc_test_people where role_name='b'),false);
select set_config('request.jwt.claim.sub',(select id::text from rpc_test_people where role_name='b'),true);
select pg_temp.assert_true((select count(*) from public.cart_items)=1,'unblocked user sees own preserved cart');
select pg_temp.expect_error(format('select public.checkout_cart(%L,''[{"product_id":2000000001,"quantity":1}]''::jsonb,%L)',gen_random_uuid(),(select value from rpc_test_meta where key='promo_code')),array['P0001','22023'],'exhausted promo cannot be reused');
select pg_temp.assert_true((select count(*) from public.cart_items)=1,'failed promo leaves cart intact');
select public.checkout_cart(gen_random_uuid(),'[{"product_id":2000000001,"quantity":1}]'::jsonb,null);
select pg_temp.assert_true((select count(*) from public.orders)=2,'second customer sees only own preexisting and new orders');

-- Create isolated carts for bad product states, never touching original users.
reset role;
insert into public.cart_items(id,user_id,product_id,product_name,price,quantity)
 select 2000000040+row_number() over(order by p.id),u.id,p.id,'RPC bad state',1,1
 from rpc_test_products p cross join rpc_test_people u where p.available and
  ((p.kind='nan' and u.role_name='signup') or (p.kind='null_stock' and u.role_name='a'));
set local role authenticated;
select set_config('request.jwt.claim.sub',(select id::text from rpc_test_people where role_name='signup'),true);
do $$declare payload jsonb;begin
 select jsonb_agg(jsonb_build_object('product_id',product_id,'quantity',quantity) order by product_id) into payload from public.cart_items;
 if payload is not null then
  perform pg_temp.expect_error(format('select public.checkout_cart(%L,%L::jsonb,null)',gen_random_uuid(),payload::text),array['P0001','22023','23514'],'NaN source price cannot create an order');
 end if;
 perform pg_temp.assert_true((select count(*) from public.orders)=0,'bad source state leaves no order');
end$$;
select set_config('request.jwt.claim.sub',(select id::text from rpc_test_people where role_name='a'),true);
do $$declare payload jsonb;begin
 select jsonb_agg(jsonb_build_object('product_id',product_id,'quantity',quantity) order by product_id) into payload from public.cart_items;
 if payload is not null then
  perform pg_temp.expect_error(format('select public.checkout_cart(%L,%L::jsonb,null)',gen_random_uuid(),payload::text),array['P0001','22023','23514'],'null source stock cannot create an order');
 end if;
 perform pg_temp.assert_true((select count(*) from public.orders)=1,'null source stock leaves only the previous valid order');
end$$;
reset role;
select pg_temp.assert_true((select stock from public.products where id=2000000001)=8,'only the two successful orders consume stock');
select pg_temp.assert_true((select used_count from public.promo_codes where id=2000000011)=1,'retries and failed promo leave usage unchanged');
select pg_temp.assert_true((select count(*) from private.checkout_requests where user_id in (select id from rpc_test_people))=2,'only successful requests are retained');
select 'extra checkout/RLS regression checks passed' as result;
rollback;
