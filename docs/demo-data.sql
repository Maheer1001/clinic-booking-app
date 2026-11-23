-- Demo patient with bookings, test results and prescriptions, for trying the app out.
--
-- Everything here is fictional. Load docs/sample-data.sql first (clinics and services), and start the
-- app once beforehand so it creates the remaining tables. Dates are relative to today, so the demo
-- always has upcoming appointments. Re-running the script replaces the demo patient's data.
--
--   psql -h localhost -U clinic_app -d clinic_booking -f docs/demo-data.sql
--
-- Sign in as  alex.morgan@example.com  /  DemoPass2026!

-- Remove a previous run (children first).
DELETE FROM "AppointmentReminder" WHERE user_id = 1001;
DELETE FROM "PrescriptionReminder" WHERE user_id = 1001;
DELETE FROM "BookingReminder" WHERE user_id = 1001;
DELETE FROM "Prescription" WHERE user_id = 1001;
DELETE FROM "TestResult" WHERE test_id IN (SELECT test_id FROM "Test" WHERE user_id = 1001);
DELETE FROM "Test" WHERE user_id = 1001;
DELETE FROM "Appointment" WHERE user_id = 1001;
DELETE FROM "Timeslot" WHERE timeslot_id BETWEEN 9001 AND 9099;
DELETE FROM "Session" WHERE user_id = 1001;
DELETE FROM "User" WHERE user_id = 1001;

-- Password: DemoPass2026! (PBKDF2, the same format the app writes on sign-up)
INSERT INTO "User" (user_id, first_name, last_name, email, password_hash, phone_number, dob, email_verified, created, updated)
VALUES (1001, 'Alex', 'Morgan', 'alex.morgan@example.com',
        'pbkdf2$210000$ovHXFAm3ZuGG6q7Az+Epig==$2j9AL+4EFvg/OroYYGjz/THPeZM7A/9WNwsdlX+z68o=',
        '07700900123', DATE '1999-04-12', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO "Medicine" (medicine_id, name) VALUES
    (9001, 'Doxycycline'),
    (9002, 'Desogestrel'),
    (9003, 'Emtricitabine/Tenofovir (PrEP)')
ON CONFLICT (medicine_id) DO NOTHING;

-- Weekday helper: the n-th Monday-Friday after today (n = 0 is the first).
CREATE OR REPLACE FUNCTION pg_temp.weekday_ahead(n INTEGER) RETURNS DATE AS $$
    SELECT d::date FROM generate_series(CURRENT_DATE + 1, CURRENT_DATE + 30, INTERVAL '1 day') d
    WHERE EXTRACT(ISODOW FROM d) <= 5 ORDER BY d OFFSET n LIMIT 1
$$ LANGUAGE sql;

-- Booked slots line up with the slots the app generates from opening hours.
INSERT INTO "Timeslot" (timeslot_id, date, start_time, end_time, service_id, clinic_id, capacity, booked_count) VALUES
    -- upcoming
    (9001, pg_temp.weekday_ahead(1), TIME '10:00', TIME '10:30', 1, 1, 1, 1),
    (9002, pg_temp.weekday_ahead(6), TIME '14:00', TIME '14:15', 3, 2, 1, 1),
    -- past
    (9011, CURRENT_DATE - 42, TIME '09:30', TIME '10:00', 1, 1, 1, 1),
    (9012, CURRENT_DATE - 120, TIME '11:00', TIME '11:15', 3, 2, 1, 1),
    (9013, CURRENT_DATE - 30, TIME '15:00', TIME '15:20', 2, 3, 1, 1);

INSERT INTO "Appointment" (app_id, user_id, timeslot_id, notes, status) VALUES
    (9001, 1001, 9001, 'Routine check-up', 'CONFIRMED'),
    (9002, 1001, 9002, NULL, 'CONFIRMED'),
    (9011, 1001, 9011, 'Follow-up after symptoms', 'COMPLETED'),
    (9012, 1001, 9012, NULL, 'COMPLETED'),
    (9013, 1001, 9013, 'Discuss switching pill', 'COMPLETED');

INSERT INTO "Test" (test_id, user_id, clinic_id, test_type, test_date, clinician_notes, treatment_instructions) VALUES
    (9001, 1001, 1, 'Full STI screen', CURRENT_DATE - 42,
        'Chlamydia detected. Partner notification discussed.',
        'Take doxycycline 100mg twice a day for 7 days. Avoid sex until treatment is finished.'),
    (9002, 1001, 2, 'HIV test', CURRENT_DATE - 120, 'No concerns.', NULL),
    (9003, 1001, 1, 'Retest (chlamydia)', CURRENT_DATE - 5, 'Retest after treatment.', NULL);

INSERT INTO "TestResult" (test_id, condition, result) VALUES
    (9001, 'Chlamydia', 'POSITIVE'),
    (9001, 'Gonorrhoea', 'NEGATIVE'),
    (9001, 'Syphilis', 'NEGATIVE'),
    (9001, 'HIV', 'NEGATIVE'),
    (9002, 'HIV', 'NEGATIVE'),
    (9003, 'Chlamydia', 'PENDING');

INSERT INTO "Prescription" (prescription_id, user_id, clinic_id, medicine_id, test_id, dosage, doses_per_day,
                            days_between_doses, start_date, end_date, instructions, actively_taking) VALUES
    (9001, 1001, 1, 9001, 9001, '100mg', 2, 0, CURRENT_DATE - 41, CURRENT_DATE - 35,
        'Take with water, not lying down. Avoid sun exposure.', false),
    (9002, 1001, 3, 9002, NULL, '75mcg', 1, 0, CURRENT_DATE - 30, CURRENT_DATE + 60,
        'Take at the same time every day.', true),
    (9003, 1001, 2, 9003, NULL, '200mg/245mg', 1, 0, CURRENT_DATE - 10, CURRENT_DATE + 80,
        'One tablet a day with or without food.', true)
ON CONFLICT (prescription_id) DO NOTHING;

-- Results are only shown to the patient once staff release them; the demo results are released.
UPDATE "Test" SET released_at = test_date WHERE user_id = 1001 AND released_at IS NULL;

-- Keep the SERIAL sequences ahead of the explicit ids above.
SELECT setval(pg_get_serial_sequence('"Medicine"', 'medicine_id'), (SELECT MAX(medicine_id) FROM "Medicine"));
SELECT setval(pg_get_serial_sequence('"Timeslot"', 'timeslot_id'), (SELECT MAX(timeslot_id) FROM "Timeslot"));
SELECT setval(pg_get_serial_sequence('"Appointment"', 'app_id'), (SELECT MAX(app_id) FROM "Appointment"));
SELECT setval(pg_get_serial_sequence('"Test"', 'test_id'), (SELECT MAX(test_id) FROM "Test"));
SELECT setval(pg_get_serial_sequence('"Prescription"', 'prescription_id'), (SELECT MAX(prescription_id) FROM "Prescription"));
