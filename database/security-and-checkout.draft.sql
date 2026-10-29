-- Reviewed deployment source. Deployment status is recorded in docs/data-access.md.
-- Verified against the real schema/backup from 2026-10-25; never apply bootstrap.sql remotely.
begin;
set local lock_timeout = '5s';
-- Fail closed rather than silently combining new and old permissive policies.
do $$ begin
  if exists (select 1 from pg_catalog.pg_policies where schemaname = 'public' and tablename in
      ('profiles', 'products', 'cart_items', 'orders', 'order_items', 'promo_codes', 'login_attempts'))
    then raise exception 'Existing policies require review before applying this draft'; end if;
end $$;
-- Old public RPCs must not remain an alternate path around the new access model.
revoke execute on all functions in schema public from public, anon, authenticated;
revoke all on all sequences in schema public from public, anon, authenticated;
revoke create on schema public from public, anon, authenticated;
-- These legacy invoker/trigger functions reference trusted public objects. No client
-- can create shadow objects in public, and pg_temp is explicitly searched last.
do $$ declare f record; begin
  for f in select p.oid::regprocedure as signature from pg_catalog.pg_proc p
      join pg_catalog.pg_namespace n on n.oid=p.pronamespace where n.nspname='public' loop
    execute pg_catalog.format('alter function %s set search_path = pg_catalog, public, pg_temp', f.signature);
  end loop;
end $$;

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;
-- Current data was checked before deployment; no product/order rows are rewritten.
alter table public.products add constraint products_checkout_price_finite
  check (price is not null and price >= 0 and price::text not in ('NaN','Infinity','-Infinity'));
alter table public.products add constraint products_checkout_stock_valid
  check (stock is not null and stock >= 0);
alter table public.promo_codes add constraint promo_codes_checkout_valid
  check (code is not null and code=upper(btrim(code)) and length(code)>0
    and discount_percent is not null and discount_percent::text not in ('NaN','Infinity','-Infinity')
    and discount_percent between 0 and 100 and expiry_date is not null
    and used_count is not null and used_count >= 0 and max_uses is not null and max_uses >= used_count
    and is_active is not null);

-- Role flags cannot be changed through profile self-service updates.
revoke all on public.profiles, public.products, public.cart_items, public.orders,
  public.order_items, public.promo_codes, public.login_attempts from public, anon, authenticated;
grant select on public.products to anon, authenticated;
grant select on public.profiles, public.cart_items, public.orders, public.order_items,
  public.promo_codes to authenticated;
grant insert (id, name, surname, email, phone, city, address) on public.profiles to authenticated;
grant update (name, surname, phone, city, address) on public.profiles to authenticated;
grant insert (user_id, product_id, product_name, price, quantity, product_image) on public.cart_items to authenticated;
grant update (quantity) on public.cart_items to authenticated;
grant delete on public.cart_items to authenticated;
grant insert (name, description, price, image_url, category, manufacturer, stock),
  update (name, description, price, image_url, category, manufacturer, stock) on public.products to authenticated;
grant update (status) on public.orders to authenticated;
grant insert (code, discount_percent, expiry_date, max_uses, used_count, is_active),
  update (code, discount_percent, expiry_date, max_uses, is_active) on public.promo_codes to authenticated;
grant usage on sequence public.products_id_seq, public.cart_items_id_seq, public.promo_codes_id_seq to authenticated;

-- Check current DB flags on every operation; never authorize from user_metadata.
create or replace function private.has_permission(p_permission text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.profiles p where p.id = (select auth.uid())
      and not coalesce(p.is_blocked, false) and not coalesce(p.blocked, false)
      and case p_permission when 'user' then true
          when 'admin' then coalesce(p.is_admin, false)
          when 'staff' then coalesce(p.is_admin, false) or coalesce(p.is_manager, false)
          else false end
  );
$$;
revoke all on function private.has_permission(text) from public, anon;
grant execute on function private.has_permission(text) to authenticated;
create or replace function private.current_user_email() returns text
language sql stable security definer set search_path = '' as $$
  select u.email::text from auth.users u where u.id=(select auth.uid());
$$;
revoke all on function private.current_user_email() from public, anon;
grant execute on function private.current_user_email() to authenticated;

alter table public.profiles enable row level security;
alter table public.products enable row level security;
alter table public.cart_items enable row level security;
alter table public.orders enable row level security;
alter table public.order_items enable row level security;
alter table public.promo_codes enable row level security;
alter table public.login_attempts enable row level security;
-- Review/drop any older permissive policies before using this draft: policies combine with OR.
create policy profile_read on public.profiles for select to authenticated
  using (id = (select auth.uid()) or (select private.has_permission('staff')));
create policy profile_create on public.profiles for insert to authenticated
  with check (id = (select auth.uid()) and not coalesce(is_admin, false) and not coalesce(is_manager, false)
      and not coalesce(is_blocked, false) and not coalesce(blocked, false)
      and email is not null and lower(email)=lower((select private.current_user_email())));
create policy profile_edit on public.profiles for update to authenticated
  using (id = (select auth.uid()) and (select private.has_permission('user')))
  with check (id = (select auth.uid()) and (select private.has_permission('user')));
create policy catalog_read on public.products for select to anon, authenticated using (true);
create policy catalog_create on public.products for insert to authenticated
  with check ((select private.has_permission('staff')));
create policy catalog_edit on public.products for update to authenticated
  using ((select private.has_permission('staff'))) with check ((select private.has_permission('staff')));
create policy cart_own on public.cart_items for all to authenticated
  using (user_id = (select auth.uid()) and (select private.has_permission('user')))
  with check (user_id = (select auth.uid()) and (select private.has_permission('user')));
create policy orders_read on public.orders for select to authenticated
  using ((user_id = (select auth.uid()) and (select private.has_permission('user'))) or (select private.has_permission('staff')));
create policy orders_staff_status on public.orders for update to authenticated
  using ((select private.has_permission('staff'))) with check ((select private.has_permission('staff')));
create policy order_items_read on public.order_items for select to authenticated
  using (exists (select 1 from public.orders o where o.id = order_id));
create policy promo_read on public.promo_codes for select to authenticated
  using ((select private.has_permission('user')));
create policy promo_create on public.promo_codes for insert to authenticated
  with check ((select private.has_permission('admin')) and used_count = 0);
create policy promo_edit on public.promo_codes for update to authenticated
  using ((select private.has_permission('admin'))) with check ((select private.has_permission('admin')));
-- login_attempts: deliberately no public read/write. Record attempts on a trusted server.

create table private.checkout_requests (
  user_id uuid not null references auth.users(id) on delete cascade,
  request_id uuid not null,
  payload_hash text not null,
  order_id integer not null references public.orders(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, request_id)
);
alter table private.checkout_requests enable row level security;
revoke all on private.checkout_requests from public, anon, authenticated;

create or replace function private.checkout_cart(p_request_id uuid, p_items jsonb, p_promo_code text)
returns integer language plpgsql security definer set search_path = '' as $$
declare
  v_user uuid := auth.uid();
  v_items jsonb;
  v_cart_ids integer[];
  v_hash text;
  v_existing private.checkout_requests%rowtype;
  v_product public.products%rowtype;
  v_promo public.promo_codes%rowtype;
  v_item record;
  v_order integer;
  v_total numeric := 0;
  v_discount numeric := 0;
  v_code text := nullif(upper(btrim(p_promo_code)), '');
begin
  if v_user is null then raise exception 'Not authorized' using errcode = '42501'; end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_user::text, 0));
  -- Serialize profile blocking/deletion with checkout, and recheck after waiting.
  perform 1 from public.profiles where id=v_user for share;
  if not private.has_permission('user') then raise exception 'Not authorized' using errcode = '42501'; end if;
  if p_request_id is null or p_items is null or jsonb_typeof(p_items) <> 'array'
      or jsonb_array_length(p_items) not between 1 and 100 then raise exception 'Invalid cart' using errcode='22023'; end if;
  if exists (select 1 from jsonb_array_elements(p_items) e where
      jsonb_typeof(e) is distinct from 'object'
      or jsonb_typeof(e->'product_id') is distinct from 'number'
      or jsonb_typeof(e->'quantity') is distinct from 'number'
      or (e->>'product_id') !~ '^[0-9]+$' or (e->>'quantity') !~ '^[0-9]+$')
    then raise exception 'Invalid cart item types' using errcode='22023'; end if;
  if exists (select 1 from jsonb_to_recordset(p_items) as x(product_id integer, quantity integer)
      where product_id is null or product_id <= 0 or quantity is null or quantity not between 1 and 10000)
      or (select count(*) <> count(distinct product_id) from jsonb_to_recordset(p_items) as x(product_id integer))
      then raise exception 'Invalid cart items'; end if;
  select jsonb_agg(jsonb_build_object('product_id', product_id, 'quantity', quantity) order by product_id)
    into v_items from jsonb_to_recordset(p_items) as x(product_id integer, quantity integer);
  v_hash := pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(v_items::text || ':' || coalesce(v_code, ''), 'UTF8')), 'hex');
  -- The account lock also serializes retries using different request IDs.
  select * into v_existing from private.checkout_requests where user_id = v_user and request_id = p_request_id;
  if found then
    if v_existing.payload_hash <> v_hash then raise exception 'Request id reused with different cart'; end if;
    return v_existing.order_id;
  end if;
  select array_agg(id) into v_cart_ids from (select id from public.cart_items where user_id = v_user order by id for update) locked_cart;
  if v_items is distinct from (select jsonb_agg(jsonb_build_object('product_id', product_id, 'quantity', quantity) order by product_id)
      from (select product_id, sum(quantity)::integer as quantity from public.cart_items where id = any(v_cart_ids) group by product_id) c)
      then raise exception 'Cart changed; reload and retry' using errcode = '40001'; end if;
  -- Lock in a stable order before calculating or modifying stock.
  perform 1 from public.products where id in (select product_id from jsonb_to_recordset(v_items) as x(product_id integer)) order by id for update;
  for v_item in select * from jsonb_to_recordset(v_items) as x(product_id integer, quantity integer) order by product_id loop
    select * into v_product from public.products where id = v_item.product_id;
    if not found or coalesce(v_product.stock, 0) < v_item.quantity or v_product.price is null
      or v_product.price < 0 or v_product.price::text in ('NaN','Infinity','-Infinity') then raise exception 'Product unavailable'; end if;
    v_total := v_total + round(v_product.price * v_item.quantity, 2);
  end loop;
  if v_code is not null then
    select * into v_promo from public.promo_codes where code = v_code for update;
    if not found or v_promo.is_active is distinct from true or v_promo.expiry_date is null or v_promo.expiry_date < current_date
      or v_promo.used_count is null or v_promo.max_uses is null or v_promo.used_count >= v_promo.max_uses
      or v_promo.discount_percent is null or v_promo.discount_percent::text in ('NaN','Infinity','-Infinity')
      or v_promo.discount_percent not between 0 and 100 then raise exception 'Promo code unavailable'; end if;
    v_discount := round(v_total * v_promo.discount_percent / 100, 2);
  end if;
  insert into public.orders(user_id, total_amount, final_amount, discount_amount, promo_code, promo_code_id, status)
    values (v_user, v_total, v_total - v_discount, v_discount, v_code, v_promo.id, 'pending') returning id into v_order;
  for v_item in select * from jsonb_to_recordset(v_items) as x(product_id integer, quantity integer) order by product_id loop
    select * into v_product from public.products where id = v_item.product_id;
    insert into public.order_items(order_id, product_id, product_name, product_image, price, quantity, subtotal)
      values (v_order, v_product.id, v_product.name, v_product.image_url, v_product.price, v_item.quantity, round(v_product.price * v_item.quantity, 2));
    update public.products set stock = stock - v_item.quantity, updated_at = now() where id = v_product.id;
    -- Rows newly inserted concurrently after the snapshot must not be erased.
    delete from public.cart_items where id = any(v_cart_ids) and product_id = v_item.product_id;
  end loop;
  if v_promo.id is not null then update public.promo_codes set used_count = used_count + 1 where id = v_promo.id; end if;
  insert into private.checkout_requests(user_id, request_id, payload_hash, order_id) values (v_user, p_request_id, v_hash, v_order);
  return v_order;
end;
$$;
revoke all on function private.checkout_cart(uuid, jsonb, text) from public, anon;
grant execute on function private.checkout_cart(uuid, jsonb, text) to authenticated;
-- Exposed wrapper keeps privileged implementation out of the exposed schema.
create or replace function public.checkout_cart(p_request_id uuid, p_items jsonb, p_promo_code text default null)
returns integer language sql security invoker set search_path = '' as $$
  select private.checkout_cart(p_request_id, p_items, p_promo_code);
$$;
revoke all on function public.checkout_cart(uuid, jsonb, text) from public, anon;
grant execute on function public.checkout_cart(uuid, jsonb, text) to authenticated;

create or replace function private.set_user_blocked(p_user_id uuid, p_blocked boolean)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null or not private.has_permission('admin') then raise exception 'Not authorized' using errcode = '42501'; end if;
  if p_user_id = auth.uid() then raise exception 'Cannot block own account'; end if;
  if p_user_id is null or p_blocked is null then raise exception 'Missing input'; end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_user_id::text, 0));
  if not private.has_permission('admin') then raise exception 'Not authorized' using errcode='42501'; end if;
  update public.profiles set is_blocked = p_blocked, blocked = p_blocked, updated_at = now() where id = p_user_id;
  if not found then raise exception 'Profile not found'; end if;
end;
$$;
revoke all on function private.set_user_blocked(uuid, boolean) from public, anon;
grant execute on function private.set_user_blocked(uuid, boolean) to authenticated;
create or replace function public.set_user_blocked(p_user_id uuid, p_blocked boolean)
returns void language sql security invoker set search_path = '' as $$ select private.set_user_blocked(p_user_id, p_blocked); $$;
revoke all on function public.set_user_blocked(uuid, boolean) from public, anon;
grant execute on function public.set_user_blocked(uuid, boolean) to authenticated;
-- Preserve public product images; only active staff may upload/replace/delete.
drop policy if exists "Allow public reads" on storage.objects;
drop policy if exists "Allow public uploads" on storage.objects;
drop policy if exists "Allow public deletes" on storage.objects;
create policy product_images_read on storage.objects for select to anon, authenticated
  using (bucket_id='product-images');
create policy product_images_insert on storage.objects for insert to authenticated
  with check (bucket_id='product-images' and (select private.has_permission('staff')));
create policy product_images_update on storage.objects for update to authenticated
  using (bucket_id='product-images' and (select private.has_permission('staff')))
  with check (bucket_id='product-images' and (select private.has_permission('staff')));
create policy product_images_delete on storage.objects for delete to authenticated
  using (bucket_id='product-images' and (select private.has_permission('staff')));

-- Existing idx_cart_*, idx_orders_*, idx_order_items_* and idx_login_attempts_*
-- already cover the RLS/FK lookups; retain them rather than creating duplicates.
create index if not exists checkout_requests_order_id_idx on private.checkout_requests(order_id);
notify pgrst, 'reload schema';
commit;
