-- Harden the technical migration user credentials and email.
UPDATE users
SET email = 'system-migration@invalid.example',
    password = '$2a$10$RNVBIPoRq3n5A5XaDRxbtOO/ASvFyeTf23KxM1Dm4rqkEHwo4497i',
    active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'system_migration'
  AND email = 'system-migration@bankingsystem.com';