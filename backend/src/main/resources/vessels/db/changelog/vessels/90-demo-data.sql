--liquibase formatted sql

--changeset ite4120:vessels-90-demo-data context:demo
--comment Maritime vessels, ownership history, and license demo rows
/* Demo data for local development and integration testing.
   Owner isikukoods correspond to mock population registry profiles:
   38102130265, 47503121234, 50011020017. */
insert into vessels.vessel (registry_code, name, type_code, build_year, home_port, gross_tonnage, owner_isikukood) values
    ('EST-1001', 'Tuulelind',      'FISHING',   2018, 'Tallinn',  145.50,  '38102130265'),
    ('EST-2002', 'Läänemere Täht', 'PASSENGER', 2020, 'Pärnu',    820.00,  '47503121234'),
    ('EST-3003', 'Nordic Carrier', 'CARGO',     2015, 'Muuga',    3200.00, '50011020017');

/* Retired vessel: soft-deleted with sys_status = 'D' and retirement fields. */
insert into vessels.vessel (registry_code, name, type_code, build_year, home_port, gross_tonnage, owner_isikukood, retired_on, retirement_reason, retirement_note, sys_status) values
    ('EST-9999', 'Vana Pukser',    'TUG',       1995, 'Paldiski', 350.00,  '38102130265', '2026-01-15', 'SCRAPPED', 'Scrapped after 30 years of harbor service', 'D');

/* Ownership history periods */
insert into vessels.vessel_ownership (vessel_id, owner_isikukood, owned_from, owned_until)
select v.id, o.owner_isikukood, o.owned_from::date, o.owned_until::date
from vessels.vessel v
join (values
    ('EST-1001', '38102130265', '2024-01-10', null),
    ('EST-2002', '38102130265', '2020-03-01', '2023-05-15'),
    ('EST-2002', '47503121234', '2023-05-15', null),
    ('EST-3003', '50011020017', '2022-08-01', null),
    ('EST-9999', '38102130265', '2010-05-01', '2026-01-15')
) as o(registry_code, owner_isikukood, owned_from, owned_until)
  on o.registry_code = v.registry_code;

/* Licenses */
insert into vessels.license (vessel_id, license_number, license_type, issued_on, valid_until, status, revoked_on, revocation_reason)
select v.id, l.license_number, l.license_type, l.issued_on::date, l.valid_until::date, l.status, l.revoked_on::date, l.revocation_reason
from vessels.vessel v
join (values
    ('EST-1001', 'LIC-F-101', 'FISHING',    '2025-01-01', '2027-01-01', 'ACTIVE',  null,         null),
    ('EST-1001', 'LIC-N-102', 'NAVIGATION', '2025-11-01', '2026-11-30', 'ACTIVE',  null,         null),
    ('EST-2002', 'LIC-P-201', 'PASSENGER',  '2026-01-01', '2027-01-01', 'ACTIVE',  null,         null),
    ('EST-2002', 'LIC-N-202', 'NAVIGATION', '2024-01-01', '2025-12-31', 'ACTIVE',  null,         null),
    ('EST-3003', 'LIC-C-301', 'CARGO',      '2025-06-01', '2027-06-01', 'ACTIVE',  null,         null),
    ('EST-3003', 'LIC-S-302', 'SAFETY',     '2025-01-01', '2026-06-01', 'REVOKED', '2025-09-15', 'Failed fire equipment inspection'),
    ('EST-9999', 'LIC-T-901', 'NAVIGATION', '2025-01-01', '2027-01-01', 'REVOKED', '2026-01-15', 'Vessel retired')
) as l(registry_code, license_number, license_type, issued_on, valid_until, status, revoked_on, revocation_reason)
  on l.registry_code = v.registry_code;
--
