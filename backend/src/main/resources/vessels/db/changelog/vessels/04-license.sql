--liquibase formatted sql

--changeset ite4120:vessels-04-license
--comment Maritime operational license business table
/* Operational licenses issued to vessels with validity periods and revocation audit. */
create table vessels.license (
    id                  bigint default nextval('core.seq_id') not null,
    vessel_id           bigint not null,
    license_number      text not null,
    license_type        text not null,
    issued_on           date not null,
    valid_until         date not null,
    status              text default 'ACTIVE' not null,
    revoked_on          date,
    revocation_reason   text,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint license_pk primary key (id),
    constraint license_vessel_fk foreign key (vessel_id) references vessels.vessel (id),
    constraint license_valid_dates_chk check (valid_until >= issued_on),
    constraint license_status_chk check (status in ('ACTIVE', 'REVOKED')),
    constraint license_revoked_chk check ((status = 'ACTIVE' and revoked_on is null and revocation_reason is null) or (status = 'REVOKED' and revoked_on is not null and revocation_reason is not null and btrim(revocation_reason) != '' and revoked_on >= issued_on))
);

select core.create_table_metadata('vessels.license');

create index license_vessel_idx on vessels.license (vessel_id) where (sys_status = 'A');
create index license_expiring_idx on vessels.license (valid_until, license_type) where (sys_status = 'A' and status = 'ACTIVE');
--
