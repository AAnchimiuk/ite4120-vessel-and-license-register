--liquibase formatted sql

--changeset ite4120:vessels-02-retirement-reason
--comment Retirement reason reference table
/* Lookup table for vessel retirement reasons. Natural-key reference data:
   no core.seq_id, no sys columns. */
create table vessels.retirement_reason (
    code                text not null,
    name                text not null,
    constraint retirement_reason_pk primary key (code)
);
--

--changeset ite4120:vessels-02-retirement-reason-seed
--comment Seed the retirement reason reference list
/* Reference data is in its own changeset, separate from DDL, and convergent
   via ON CONFLICT DO NOTHING. */
insert into vessels.retirement_reason (code, name) values
    ('SCRAPPED',  'Scrapped'),
    ('LOST',      'Lost at sea'),
    ('REFLAGGED', 'Re-flagged abroad'),
    ('OTHER',     'Other')
on conflict do nothing;
--
