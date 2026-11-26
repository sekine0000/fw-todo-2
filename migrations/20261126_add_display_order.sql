-- Existing installations are also upgraded automatically at application startup.
ALTER TABLE todos ADD COLUMN display_order INT NOT NULL DEFAULT 2147483647;
