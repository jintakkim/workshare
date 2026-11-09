ALTER TABLE employee ADD COLUMN excluded_by_admin BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE employee SET excluded_by_admin = TRUE WHERE inactive_from IS NOT NULL;

UPDATE employee
SET inactive_from = LEAST(
    COALESCE(inactive_from, CAST('9999-12-31' AS DATE)),
    CASE
        WHEN extended_service_end_date IS NOT NULL THEN DATE_ADD(extended_service_end_date, INTERVAL 1 DAY)
        WHEN DAYOFMONTH(DATE_ADD(active_from, INTERVAL 21 MONTH)) = DAYOFMONTH(active_from)
            THEN DATE_ADD(active_from, INTERVAL 21 MONTH)
        ELSE DATE_ADD(DATE_ADD(active_from, INTERVAL 21 MONTH), INTERVAL 1 DAY)
    END
);

ALTER TABLE employee MODIFY COLUMN inactive_from DATE NOT NULL, DROP COLUMN extended_service_end_date;
