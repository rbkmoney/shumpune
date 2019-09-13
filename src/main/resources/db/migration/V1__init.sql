create schema if not exists shm;

CREATE TYPE shm.posting_operation_type AS ENUM ('HOLD', 'COMMIT', 'ROLLBACK');

CREATE TABLE shm.account
(
  id            bigserial                   NOT NULL,
  curr_sym_code character varying           NOT NULL,
  creation_time timestamp without time zone NOT NULL,
  description   character varying(4096),
  CONSTRAINT account_pkey PRIMARY KEY (id)
)
  WITH (
    OIDS= FALSE
  );

CREATE TABLE shm.account_log
(
  id                   bigserial                   NOT NULL,
  account_id           bigint                      NOT NULL,
  own_amount           BIGINT                      NULL     DEFAULT 0,
  max_available_amount BIGINT                      NULL     DEFAULT 0,
  min_available_amount BIGINT                      NULL     DEFAULT 0,
  clock                BIGINT                      NOT NULL DEFAULT 0,
  creation_time        timestamp without time zone NOT NULL,
  CONSTRAINT account_log_pkey PRIMARY KEY (id)
)
  WITH (
    OIDS= FALSE
  );

CREATE TABLE shm.posting_log
(
  id              bigserial                   NOT NULL,
  plan_id         character varying(64)       NOT NULL,
  batch_id        bigint                      NOT NULL,
  from_account_id bigint                      NOT NULL,
  to_account_id   bigint                      NOT NULL,
  operation       shm.posting_operation_type  NOT NULL,
  amount          bigint                      NOT NULL,
  creation_time   timestamp without time zone NOT NULL,
  curr_sym_code   character varying(4)        NOT NULL,
  description     character varying,
  CONSTRAINT posting_log_pkey PRIMARY KEY (id)
)
  WITH (
    OIDS= FALSE
  );

CREATE INDEX posting_log_plan_id_idx
  ON shm.posting_log
    USING btree
    (plan_id COLLATE pg_catalog."default", batch_id);