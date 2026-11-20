--liquibase formatted sql

--changeset ite4120:vessels-03-vessel
--comment The main vessel register business table
/* Platform idioms on display:
   - id from the SHARED sequence core.seq_id;
   - sys_* columns filled by core.sys_columns() trigger via core.create_table_metadata();
   - soft delete: retiring a vessel sets sys_status = 'D' and stamps retired_on;
   - registry_code unique among active rows only (where sys_status = 'A');
   - owner_isikukood is TEXT — an identifier string of 11 digits, never a number. */
create table vessels.vessel (
    id                  bigint default nextval('core.seq_id') not null,
    registry_code       text not null,
    name                text not null,
    type_code           text not null,
    build_year          integer,
    home_port           text,
    gross_tonnage       numeric,
    owner_isikukood     text,
    retired_on          date,
    retirement_reason   text,
    retirement_note     text,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint vessel_pk primary key (id),
    constraint vessel_type_fk foreign key (type_code) references vessels.vessel_type (code),
    constraint vessel_retirement_reason_fk foreign key (retirement_reason) references vessels.retirement_reason (code),
    constraint vessel_isikukood_chk check (owner_isikukood is null or owner_isikukood ~ '^[0-9]{11}$'),
    constraint vessel_gross_tonnage_chk check (gross_tonnage is null or gross_tonnage > 0),
    constraint vessel_build_year_chk check (build_year is null or build_year >= 1800),
    constraint vessel_retirement_reason_chk check (retired_on is null or retirement_reason is not null),
    constraint vessel_retirement_note_chk check (retirement_reason is null or retirement_reason != 'OTHER' or (retirement_note is not null and btrim(retirement_note) != ''))
);

select core.create_table_metadata('vessels.vessel');

/* Unique among ACTIVE rows only: a retired vessel's code may be reused. */
create unique index vessel_registry_code_ukey
    on vessels.vessel (registry_code) where (sys_status = 'A');

create index vessel_type_idx on vessels.vessel (type_code) where (sys_status = 'A');
create index vessel_retired_idx on vessels.vessel (retired_on) where (retired_on is not null);
--
