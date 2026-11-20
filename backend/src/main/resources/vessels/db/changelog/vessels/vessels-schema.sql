--liquibase formatted sql

--changeset ite4120:vessels-schema
--comment Create the vessels schema
/* One schema per component — boundaries in the database mirror boundaries in
   the code. */
create schema if not exists vessels;
--
