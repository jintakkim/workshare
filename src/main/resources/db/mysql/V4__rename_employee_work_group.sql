ALTER TABLE employee
    DROP CHECK employee_chk_1,
    RENAME COLUMN work_group_code TO work_group,
    ADD CONSTRAINT chk_employee_work_group
        CHECK (work_group IN ('RESTAURANT', 'GENERAL', 'FACILITY_SUPPORT', 'THIRD_FLOOR', 'DEVELOPMENT_SUPPORT'));
