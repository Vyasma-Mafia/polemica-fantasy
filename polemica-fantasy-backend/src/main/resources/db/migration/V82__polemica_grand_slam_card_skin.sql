INSERT INTO card_skin (code, name)
VALUES ('polemica_grand_slam_2026', 'Polemica Grand Slam 2026')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name;
