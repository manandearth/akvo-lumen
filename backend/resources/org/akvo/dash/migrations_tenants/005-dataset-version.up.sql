CREATE TABLE dataset_version (
  id text PRIMARY KEY,
  dataset_id text REFERENCES dataset,
  job_execution_id text REFERENCES job_execution,
  version smallint NOT NULL,
  -- The name of the data table
  table_name text UNIQUE NOT NULL,
  created timestamptz DEFAULT now(),
  modified timestamptz DEFAULT now(),
  UNIQUE (dataset_id, version)
);

GRANT ALL ON dataset_version to dash;

CREATE TABLE history.dataset_version (
  LIKE public.dataset_version,
  _validrange tstzrange NOT NULL
);

GRANT ALL ON history.dataset_version to dash;

ALTER TABLE ONLY history.dataset_version
ADD CONSTRAINT dataset_version_exclusion EXCLUDE
USING gist (id WITH =, _validrange WITH &&);

CREATE TRIGGER dataset_version_history BEFORE
INSERT OR DELETE OR UPDATE ON dataset_version
FOR EACH ROW EXECUTE PROCEDURE history.log_change();