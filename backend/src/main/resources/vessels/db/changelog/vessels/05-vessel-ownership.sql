--liquibase formatted sql

--changeset ite4120:vessels-05-vessel-ownership
--comment Vessel ownership history business table
/* Maintains the audit trail of vessel ownership periods. Exactly one open period
   allowed per vessel via partial unique index. */
create table vessels.vessel_ownership (
    id                  bigint default nextval('core.seq_id') not null,
    vessel_id           bigint not null,
    owner_isikukood     text not null,
    owned_from          date not null,
    owned_until         date,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint vessel_ownership_pk primary key (id),
    constraint vessel_ownership_vessel_fk foreign key (vessel_id) references vessels.vessel (id),
    constraint vessel_ownership_isikukood_chk check (owner_isikukood ~ '^[0-9]{11}$'),
    constraint vessel_ownership_dates_chk check (owned_until is null or owned_until >= owned_from)
);

select core.create_table_metadata('vessels.vessel_ownership');

create index vessel_ownership_vessel_idx on vessels.vessel_ownership (vessel_id) where (sys_status = 'A');

/* At most one open ownership period per vessel. */
create unique index idx_vessel_ownership_current on vessels.vessel_ownership (vessel_id) where (owned_until is null);
--
