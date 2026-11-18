-- Sample clinics and services for a fresh local database.
--
-- The app creates all tables itself on startup (app.DB.initTables), but it does not add any
-- clinics or services, so the booking screens are empty on a new database. This script creates
-- the three tables it needs (same definitions as DB.initTables, so it is safe to run before or
-- after the first launch) and inserts a few rows. It can be re-run; existing rows are skipped.
--
--   psql -h localhost -U clinic_app -d clinic_booking -f docs/sample-data.sql

CREATE TABLE IF NOT EXISTS "Clinic" (
    clinic_id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    address VARCHAR(255),
    postcode VARCHAR(20),
    phone VARCHAR(20),
    rating DECIMAL(2,1),
    photo_url VARCHAR(255),
    opening_hours VARCHAR(255),
    updated_at TIMESTAMP,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION
);
ALTER TABLE "Clinic" ADD COLUMN IF NOT EXISTS disability_accessibility TEXT;

CREATE TABLE IF NOT EXISTS "Service" (
    service_id SERIAL PRIMARY KEY,
    name VARCHAR(50),
    description TEXT,
    duration_minutes INTEGER
);

CREATE TABLE IF NOT EXISTS "ClinicService" (
    clinic_id INTEGER,
    service_id INTEGER REFERENCES "Service"(service_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clinic_id, service_id)
);

-- Opening hours use the format "Day-Day: H:MM-H:MM" (a closing time before noon is read as PM).
INSERT INTO "Clinic" (clinic_id, name, address, postcode, phone, opening_hours, latitude, longitude, disability_accessibility)
VALUES
    (1, 'Southampton Central Sexual Health Clinic', '1 Example Road, Southampton', 'SO14 0YG', '023 0000 0001',
        'Monday-Friday: 9:00-5:00', 50.9050, -1.4040, 'Step-free access,Accessible toilet'),
    (2, 'Portswood Sexual Health Clinic', '2 Example Street, Southampton', 'SO17 2FW', '023 0000 0002',
        'Monday-Saturday: 10:00-6:00', 50.9230, -1.3960, 'Hearing loop'),
    (3, 'Winchester Sexual Health Clinic', '3 Example Lane, Winchester', 'SO23 8UJ', '01962 000003',
        'Tuesday-Friday: 8:30-4:30', 51.0630, -1.3110, NULL)
ON CONFLICT (clinic_id) DO NOTHING;

INSERT INTO "Service" (service_id, name, description, duration_minutes)
VALUES
    (1, 'STI test', 'Testing for common sexually transmitted infections', 30),
    (2, 'Contraception', 'Contraception advice and appointments', 20),
    (3, 'HIV test', 'Rapid HIV testing', 15)
ON CONFLICT (service_id) DO NOTHING;

INSERT INTO "ClinicService" (clinic_id, service_id)
VALUES (1, 1), (1, 2), (1, 3), (2, 1), (2, 3), (3, 1), (3, 2)
ON CONFLICT DO NOTHING;

-- Keep the SERIAL sequences ahead of the explicit ids above.
SELECT setval(pg_get_serial_sequence('"Clinic"', 'clinic_id'), (SELECT MAX(clinic_id) FROM "Clinic"));
SELECT setval(pg_get_serial_sequence('"Service"', 'service_id'), (SELECT MAX(service_id) FROM "Service"));
