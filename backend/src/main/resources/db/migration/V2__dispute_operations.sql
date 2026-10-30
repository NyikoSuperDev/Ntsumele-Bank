-- Allocate global, non-cycling decimal case references without changing existing references.
CREATE SEQUENCE dispute_case_reference_seq AS BIGINT START WITH 1 INCREMENT BY 1 NO CYCLE;
DO $$
DECLARE
  highest_reference NUMERIC;
BEGIN
  SELECT COALESCE(MAX((substring(case_reference FROM '^NB-[0-9]{4}-([0-9]+)$'))::NUMERIC), 0)
    INTO highest_reference FROM dispute_case;
  IF highest_reference >= 9223372036854775807 THEN
    RAISE EXCEPTION 'Existing case reference exceeds the available sequence range';
  END IF;
  PERFORM setval('dispute_case_reference_seq', (highest_reference + 1)::BIGINT, false);
END $$;

CREATE INDEX idx_dispute_queue_page ON dispute_case(created_at DESC, id DESC);
CREATE INDEX idx_dispute_status_page ON dispute_case(status, created_at DESC, id DESC);
CREATE INDEX idx_dispute_active_page ON dispute_case(created_at DESC, id DESC) WHERE status <> 'RESOLVED';
CREATE INDEX idx_dispute_resolved_retention ON dispute_case(updated_at, id) WHERE status = 'RESOLVED';
CREATE INDEX idx_idempotency_retention ON idempotency_record(created_at, username, request_key);
