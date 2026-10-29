#!/usr/bin/env bash
# Tests only the disposable PostgreSQL17 container created for this project.
set -euo pipefail
container="${1:-techstore-plan-postgres}"
project_root="$(cd "$(dirname "$0")/../.." && pwd)"
if [[ "$(docker inspect --format '{{.Config.Image}} {{.HostConfig.NetworkMode}}' "$container")" != 'postgres:17 none' ]]; then
    printf '%s\n' 'Refusing to run outside the isolated PostgreSQL17 test container.' >&2
    exit 1
fi
mkdir -p "$project_root/build/test-results"
test_dir="$(mktemp -d "$project_root/build/test-results/sql-concurrency.XXXXXX")"
psql_test() { docker exec -i "$container" psql -U postgres -d postgres -X -v ON_ERROR_STOP=1 "$@"; }
cleanup() {
    psql_test > /dev/null <<'SQL' || true
begin;
delete from private.checkout_requests where user_id in (
 '44444444-4444-4444-4444-444444444444','55555555-5555-5555-5555-555555555555',
 '66666666-6666-6666-6666-666666666666','77777777-7777-7777-7777-777777777777');
delete from order_items where order_id in (select id from orders where user_id in (
 '44444444-4444-4444-4444-444444444444','55555555-5555-5555-5555-555555555555',
 '66666666-6666-6666-6666-666666666666','77777777-7777-7777-7777-777777777777'));
delete from orders where user_id in (
 '44444444-4444-4444-4444-444444444444','55555555-5555-5555-5555-555555555555',
 '66666666-6666-6666-6666-666666666666','77777777-7777-7777-7777-777777777777');
delete from cart_items where product_id in (100001,100002);
delete from products where id in (100001,100002);
delete from promo_codes where id = 100001;
delete from profiles where id in (
 '44444444-4444-4444-4444-444444444444','55555555-5555-5555-5555-555555555555',
 '66666666-6666-6666-6666-666666666666','77777777-7777-7777-7777-777777777777');
delete from auth.users where id in (
 '44444444-4444-4444-4444-444444444444','55555555-5555-5555-5555-555555555555',
 '66666666-6666-6666-6666-666666666666','77777777-7777-7777-7777-777777777777');
commit;
SQL
}
trap cleanup EXIT
cleanup
psql_test > "$test_dir/setup.log" <<'SQL'
begin;
insert into auth.users(id,email) values
 ('44444444-4444-4444-4444-444444444444','stock-a@example.test'),
 ('55555555-5555-5555-5555-555555555555','stock-b@example.test'),
 ('66666666-6666-6666-6666-666666666666','promo-a@example.test'),
 ('77777777-7777-7777-7777-777777777777','promo-b@example.test');
insert into profiles(id,email) select id,email from auth.users where id in (
 '44444444-4444-4444-4444-444444444444','55555555-5555-5555-5555-555555555555',
 '66666666-6666-6666-6666-666666666666','77777777-7777-7777-7777-777777777777');
insert into products(id,name,price,stock) values(100001,'Last stock fixture',100,1),(100002,'Promo fixture',100,2);
insert into promo_codes(id,code,discount_percent,expiry_date,max_uses) values(100001,'LASTPROMO',10,current_date+1,1);
insert into cart_items(user_id,product_id,product_name,price,quantity) values
 ('44444444-4444-4444-4444-444444444444',100001,'Last stock fixture',100,1),
 ('55555555-5555-5555-5555-555555555555',100001,'Last stock fixture',100,1),
 ('66666666-6666-6666-6666-666666666666',100002,'Promo fixture',100,1),
 ('77777777-7777-7777-7777-777777777777',100002,'Promo fixture',100,1);
commit;
SQL
run_checkout() {
    local user_id="$1" request_id="$2" product_id="$3" promo="$4" output="$5"
    psql_test > "$output" 2>&1 <<SQL
begin;
set local role authenticated;
select set_config('request.jwt.claim.sub', '$user_id', true);
select public.checkout_cart('$request_id', '[{"product_id":$product_id,"quantity":1}]', $promo);
select pg_sleep(1);
commit;
SQL
}
# A successful transaction holds product/promo locks before commit; the competitor must wait.
run_checkout '44444444-4444-4444-4444-444444444444' '44444444-aaaa-aaaa-aaaa-444444444444' 100001 null "$test_dir/stock-a.log" & first=$!
run_checkout '55555555-5555-5555-5555-555555555555' '55555555-aaaa-aaaa-aaaa-555555555555' 100001 null "$test_dir/stock-b.log" & second=$!
first_status=0; second_status=0
wait "$first" || first_status=$?
wait "$second" || second_status=$?
if (( (first_status == 0) == (second_status == 0) )); then
    printf 'Expected exactly one successful last-stock checkout. Logs: %s\n' "$test_dir" >&2; exit 1
fi
run_checkout '66666666-6666-6666-6666-666666666666' '66666666-aaaa-aaaa-aaaa-666666666666' 100002 "'LASTPROMO'" "$test_dir/promo-a.log" & first=$!
run_checkout '77777777-7777-7777-7777-777777777777' '77777777-aaaa-aaaa-aaaa-777777777777' 100002 "'LASTPROMO'" "$test_dir/promo-b.log" & second=$!
first_status=0; second_status=0
wait "$first" || first_status=$?
wait "$second" || second_status=$?
if (( (first_status == 0) == (second_status == 0) )); then
    printf 'Expected exactly one successful last-promo checkout. Logs: %s\n' "$test_dir" >&2; exit 1
fi
psql_test > "$test_dir/assertions.log" <<'SQL'
do $$ begin
 if (select stock from products where id = 100001) <> 0 then raise exception 'Last-stock inventory incorrect'; end if;
 if (select count(*) from order_items where product_id = 100001) <> 1 then raise exception 'Last-stock oversold'; end if;
 if (select stock from products where id = 100002) <> 1 then raise exception 'Failed promo checkout changed stock'; end if;
 if (select used_count from promo_codes where id = 100001) <> 1 then raise exception 'Promo usage overshot limit'; end if;
 if (select count(*) from order_items where product_id = 100002) <> 1 then raise exception 'Failed promo checkout left items'; end if;
 if (select count(*) from cart_items where product_id in (100001,100002)) <> 2 then raise exception 'Failed checkout removed cart'; end if;
end $$;
SQL
printf 'PASS: last-stock and last-promo concurrent checkout. Logs: %s\n' "$test_dir"
