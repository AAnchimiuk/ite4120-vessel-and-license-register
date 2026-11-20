--liquibase formatted sql

--changeset ite4120:vessels-01-vessel-type
--comment Vessel type reference table
/* Lookup table for maritime vessel classification. Natural-key reference data:
   no core.seq_id, no sys columns. */
create table vessels.vessel_type (
    code                text not null,
    name                text not null,
    constraint vessel_type_pk primary key (code)
);
--

--changeset ite4120:vessels-01-vessel-type-seed
--comment Seed the vessel type reference list
/* Reference data is in its own changeset, separate from DDL, and convergent
   via ON CONFLICT DO NOTHING. */
insert into vessels.vessel_type (code, name) values
    ('FISHING',   'Fishing vessel'),
    ('CARGO',     'Cargo ship'),
    ('PASSENGER', 'Passenger ship'),
    ('PLEASURE',  'Pleasure craft'),
    ('SAILING',   'Sailing vessel'),
    ('TUG',       'Tugboat')
on conflict do nothing;
--
