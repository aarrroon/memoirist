INSERT INTO users (id, email, display_name, timezone)
VALUES ('00000000-0000-0000-0000-000000000001', 'dev@memoirist.local', 'Dev User', 'Australia/Melbourne')
ON CONFLICT (id) DO NOTHING;
