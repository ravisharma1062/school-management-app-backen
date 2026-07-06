-- Dev-only bootstrap admin so a fresh environment has a way to log in.
-- Password: Admin@123 (bcrypt-hashed via pgcrypto so it verifies against Spring's BCryptPasswordEncoder).
-- Change or remove this seed before any non-local deployment.
INSERT INTO users (name, email, password_hash, role)
VALUES ('System Admin', 'admin@school.app', crypt('Admin@123', gen_salt('bf', 10)), 'ADMIN');
