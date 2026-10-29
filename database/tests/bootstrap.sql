-- Disposable PostgreSQL 17 container only. Never use this bootstrap on a real Supabase project.
create role anon nologin;
create role authenticated nologin;
create schema storage;
create table storage.objects(id uuid primary key default gen_random_uuid(),bucket_id text,name text);
alter table storage.objects enable row level security;
grant usage on schema storage to anon,authenticated;
grant select,insert,update,delete on storage.objects to anon,authenticated;
create schema auth;
create table auth.users(id uuid primary key, email text);
create function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$;
grant usage on schema auth to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;
create table public.profiles (
 id uuid primary key references auth.users(id), name text, surname text, email text, phone text, city text, address text,
 is_admin boolean default false, is_manager boolean default false, is_blocked boolean default false, blocked boolean default false,
 created_at timestamptz default now(), updated_at timestamptz default now()
);
create table public.products (
 id serial primary key, name varchar not null, description text, price numeric not null,
 image_url text, category varchar, manufacturer varchar, stock integer default 0 check(stock >= 0),
 created_at timestamp default now(), updated_at timestamp default now()
);
create table public.promo_codes (
 id serial primary key, code varchar unique not null, discount_percent numeric not null check(discount_percent between 0 and 100),
 expiry_date date not null, max_uses integer not null default 100, used_count integer not null default 0 check(used_count >= 0),
 is_active boolean not null default true, created_at timestamptz default now()
);
create table public.cart_items (
 id serial primary key, user_id uuid not null references profiles(id), product_id integer not null references products(id),
 product_name varchar not null, price numeric not null, product_image text, quantity integer not null check(quantity > 0),
 created_at timestamp default now(), updated_at timestamp default now(), cart_id integer
);
create table public.orders (
 id serial primary key, user_id uuid not null references profiles(id), total_amount numeric not null, final_amount numeric not null,
 promo_code varchar, promo_code_id integer references promo_codes(id), discount_amount numeric default 0,
 status varchar default 'pending', created_at timestamp default now(), order_date timestamp default now()
);
create table public.order_items (
 id serial primary key, order_id integer not null references orders(id), product_id integer not null references products(id),
 product_name varchar not null, product_image text, price numeric not null, quantity integer not null, subtotal numeric not null
);
create table public.login_attempts (
 id serial primary key, user_id uuid references profiles(id), email text not null, success boolean not null, attempt_time timestamp default now()
);
