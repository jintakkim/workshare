ALTER TABLE employee ADD COLUMN excluded_by_admin BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE employee SET excluded_by_admin = TRUE WHERE inactive_from IS NOT NULL;

UPDATE employee
SET inactive_from = LEAST(
    COALESCE(inactive_from, DATE '9999-12-31'),
    CASE
        WHEN extended_service_end_date IS NOT NULL THEN DATEADD('DAY', 1, extended_service_end_date)
        WHEN DAYOFMONTH(DATEADD('MONTH', 21, active_from)) = DAYOFMONTH(active_from)
            THEN DATEADD('MONTH', 21, active_from)
        ELSE DATEADD('DAY', 1, DATEADD('MONTH', 21, active_from))
    END
);

ALTER TABLE employee ALTER COLUMN inactive_from SET NOT NULL;
ALTER TABLE employee DROP COLUMN extended_service_end_date;
