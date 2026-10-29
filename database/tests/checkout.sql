-- Run after bootstrap.sql + security-and-checkout.draft.sql, with ON_ERROR_STOP=1.
begin;
insert into auth.users(id,email) values
 ('11111111-1111-1111-1111-111111111111','one@example.test'),
 ('22222222-2222-2222-2222-222222222222','two@example.test'),
 ('33333333-3333-3333-3333-333333333333','admin@example.test');
insert into profiles(id,email,is_admin) select id,email,email = 'admin@example.test' from auth.users;
insert into products(id,name,price,stock) values(1,'CPU fixture',100.05,2),(2,'GPU fixture',200,0);
insert into promo_codes(id,code,discount_percent,expiry_date,max_uses) values(1,'TEST10',10,current_date+1,1);
insert into cart_items(user_id,product_id,product_name,price,quantity) values
 ('11111111-1111-1111-1111-111111111111',1,'Untrusted client name',0.01,1),
 ('22222222-2222-2222-2222-222222222222',1,'CPU fixture',100.05,1);
set local role authenticated;
select set_config('request.jwt.claim.sub','11111111-1111-1111-1111-111111111111',true);
do $$ begin
 if (select count(*) from cart_items) <> 1 then raise exception 'RLS leaked another cart'; end if;
 begin
  update profiles set is_admin = true where id = auth.uid();
  raise exception 'Role escalation was allowed';
 exception when insufficient_privilege then null; end;
end $$;
select public.checkout_cart('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '[{"product_id":1,"quantity":1}]', 'test10');
select public.checkout_cart('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '[{"product_id":1,"quantity":1}]', 'test10');
do $$ begin
 if (select count(*) from orders) <> 1 then raise exception 'Retry created duplicate order'; end if;
 if (select final_amount from orders limit 1) <> 90.04 then raise exception 'Wrong monetary rounding'; end if;
 if (select product_name from order_items limit 1) <> 'CPU fixture' then raise exception 'Trusted client product snapshot'; end if;
 if (select count(*) from cart_items) <> 0 then raise exception 'Purchased cart was not cleared'; end if;
 begin
  perform public.checkout_cart('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','[{"product_id":1,"quantity":2}]','test10');
  raise exception 'Changed payload reused request ID';
 exception when raise_exception then
  if sqlerrm = 'Changed payload reused request ID' then raise; end if;
 end;
end $$;
select set_config('request.jwt.claim.sub','22222222-2222-2222-2222-222222222222',true);
do $$ begin
 if (select count(*) from orders) <> 0 then raise exception 'RLS leaked another order'; end if;
 begin
  perform public.checkout_cart('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','[{"product_id":1,"quantity":1}]','test10');
  raise exception 'Exhausted promo accepted';
 exception when raise_exception then
  if sqlerrm = 'Exhausted promo accepted' then raise; end if;
 end;
 if (select count(*) from orders) <> 0 then raise exception 'Failed checkout left order'; end if;
end $$;
select set_config('request.jwt.claim.sub','33333333-3333-3333-3333-333333333333',true);
select public.set_user_blocked('22222222-2222-2222-2222-222222222222',true);
select set_config('request.jwt.claim.sub','22222222-2222-2222-2222-222222222222',true);
do $$ begin
 if (select count(*) from cart_items) <> 0 then raise exception 'Blocked user retains cart access'; end if;
 begin
  perform public.checkout_cart('cccccccc-cccc-cccc-cccc-cccccccccccc','[{"product_id":1,"quantity":1}]',null);
  raise exception 'Blocked user placed order';
 exception when insufficient_privilege then null; end;
end $$;
reset role;
do $$ begin
 if (select stock from products where id = 1) <> 1 then raise exception 'Retry or failed checkout changed stock'; end if;
 if (select used_count from promo_codes where id = 1) <> 1 then raise exception 'Retry incremented promo usage'; end if;
end $$;
rollback;
