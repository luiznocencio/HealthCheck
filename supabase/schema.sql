-- =====================================================================
-- HealthCheck - esquema do banco (Supabase / Postgres)
-- Execute este arquivo inteiro no SQL Editor do seu projeto Supabase.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Perfis: um por usuário autenticado
-- ---------------------------------------------------------------------
create table if not exists public.profiles (
    id          uuid primary key references auth.users (id) on delete cascade,
    name        text not null default '',
    email       text not null,
    created_at  timestamptz not null default now()
);

-- Cria o perfil automaticamente quando alguém se cadastra
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    insert into public.profiles (id, name, email)
    values (new.id, coalesce(new.raw_user_meta_data ->> 'name', ''), lower(new.email))
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------
-- Vínculos de cuidado: "caregiver" pode cadastrar remédios para "patient"
-- O paciente precisa aceitar o convite (status = 'accepted').
-- ---------------------------------------------------------------------
create table if not exists public.care_links (
    id            uuid primary key default gen_random_uuid(),
    caregiver_id  uuid not null references public.profiles (id) on delete cascade,
    patient_id    uuid not null references public.profiles (id) on delete cascade,
    status        text not null default 'pending' check (status in ('pending', 'accepted')),
    created_at    timestamptz not null default now(),
    unique (caregiver_id, patient_id),
    check (caregiver_id <> patient_id)
);

-- ---------------------------------------------------------------------
-- Remédios
--   owner_id      -> quem toma o remédio (recebe os alarmes)
--   created_by    -> quem cadastrou (o próprio usuário ou um cuidador)
--   times         -> horários do dia, ex: {'08:00','20:00'}
--   days_of_week  -> 1=segunda ... 7=domingo
-- ---------------------------------------------------------------------
create table if not exists public.medications (
    id            uuid primary key default gen_random_uuid(),
    owner_id      uuid not null references public.profiles (id) on delete cascade,
    created_by    uuid not null references public.profiles (id) on delete cascade,
    name          text not null,
    dosage        text not null default '',
    notes         text not null default '',
    times         text[] not null default '{}',
    days_of_week  int[]  not null default '{1,2,3,4,5,6,7}',
    start_date    date   not null default current_date,
    end_date      date,
    active        boolean not null default true,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);
create index if not exists medications_owner_idx on public.medications (owner_id);

-- ---------------------------------------------------------------------
-- Registro de doses (tomou / pulou) - o cuidador acompanha
-- ---------------------------------------------------------------------
create table if not exists public.dose_logs (
    id             uuid primary key default gen_random_uuid(),
    medication_id  uuid not null references public.medications (id) on delete cascade,
    owner_id       uuid not null references public.profiles (id) on delete cascade,
    scheduled_at   timestamptz not null,
    status         text not null check (status in ('taken', 'skipped')),
    logged_at      timestamptz not null default now(),
    unique (medication_id, scheduled_at)
);
create index if not exists dose_logs_owner_idx on public.dose_logs (owner_id, scheduled_at);

-- ---------------------------------------------------------------------
-- Funções auxiliares
-- ---------------------------------------------------------------------
-- O usuário atual pode gerenciar os dados de "target"?
create or replace function public.can_manage(target uuid)
returns boolean
language sql stable
security definer set search_path = public
as $$
    select target = auth.uid()
        or exists (
            select 1 from public.care_links
            where caregiver_id = auth.uid()
              and patient_id = target
              and status = 'accepted'
        );
$$;

-- Envia convite de cuidado para outra pessoa pelo e-mail
create or replace function public.request_care_link(patient_email text)
returns public.care_links
language plpgsql
security definer set search_path = public
as $$
declare
    target uuid;
    result public.care_links;
begin
    select id into target from public.profiles where email = lower(trim(patient_email));
    if target is null then
        raise exception 'Nenhum usuário encontrado com este e-mail';
    end if;
    if target = auth.uid() then
        raise exception 'Você não pode enviar convite para si mesmo';
    end if;

    insert into public.care_links (caregiver_id, patient_id)
    values (auth.uid(), target)
    on conflict (caregiver_id, patient_id) do update set caregiver_id = excluded.caregiver_id
    returning * into result;
    return result;
end;
$$;

-- Mantém updated_at atualizado
create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists medications_touch on public.medications;
create trigger medications_touch
    before update on public.medications
    for each row execute function public.touch_updated_at();

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
alter table public.profiles    enable row level security;
alter table public.care_links  enable row level security;
alter table public.medications enable row level security;
alter table public.dose_logs   enable row level security;

-- profiles: vejo o meu e de quem tenho vínculo (pendente ou aceito)
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles for select using (
    id = auth.uid()
    or exists (
        select 1 from public.care_links l
        where (l.caregiver_id = auth.uid() and l.patient_id = profiles.id)
           or (l.patient_id = auth.uid() and l.caregiver_id = profiles.id)
    )
);
drop policy if exists profiles_update on public.profiles;
create policy profiles_update on public.profiles for update
    using (id = auth.uid()) with check (id = auth.uid());

-- care_links
drop policy if exists links_select on public.care_links;
create policy links_select on public.care_links for select
    using (caregiver_id = auth.uid() or patient_id = auth.uid());
drop policy if exists links_insert on public.care_links;
create policy links_insert on public.care_links for insert
    with check (caregiver_id = auth.uid() and status = 'pending');
-- só o paciente aceita
drop policy if exists links_update on public.care_links;
create policy links_update on public.care_links for update
    using (patient_id = auth.uid()) with check (patient_id = auth.uid());
-- qualquer um dos dois pode desfazer o vínculo
drop policy if exists links_delete on public.care_links;
create policy links_delete on public.care_links for delete
    using (caregiver_id = auth.uid() or patient_id = auth.uid());

-- medications
drop policy if exists meds_select on public.medications;
create policy meds_select on public.medications for select using (public.can_manage(owner_id));
drop policy if exists meds_insert on public.medications;
create policy meds_insert on public.medications for insert
    with check (public.can_manage(owner_id) and created_by = auth.uid());
drop policy if exists meds_update on public.medications;
create policy meds_update on public.medications for update
    using (public.can_manage(owner_id)) with check (public.can_manage(owner_id));
drop policy if exists meds_delete on public.medications;
create policy meds_delete on public.medications for delete using (public.can_manage(owner_id));

-- dose_logs: o paciente registra; cuidador visualiza
drop policy if exists doses_select on public.dose_logs;
create policy doses_select on public.dose_logs for select using (public.can_manage(owner_id));
drop policy if exists doses_insert on public.dose_logs;
create policy doses_insert on public.dose_logs for insert with check (owner_id = auth.uid());
drop policy if exists doses_update on public.dose_logs;
create policy doses_update on public.dose_logs for update
    using (owner_id = auth.uid()) with check (owner_id = auth.uid());
