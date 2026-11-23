-- One account per email, ignoring case. Until now uniqueness was only checked in code
-- (UserDAO.emailExists / emailTakenByOtherUser), so two sign-ups at the same moment could both succeed.
-- app.DB.initTables creates the same index on a fresh (empty) database; existing databases get it here.
-- Idempotent: safe to run more than once.

-- 1. Report duplicates. Must return no rows before the index can be created; if it lists any,
--    merge or delete the extra accounts first (each group is one email used by several user_ids).
SELECT lower(email) AS email, count(*) AS accounts, array_agg(user_id ORDER BY user_id) AS user_ids
FROM "User"
WHERE email IS NOT NULL
GROUP BY lower(email)
HAVING count(*) > 1
ORDER BY lower(email);

-- 2. Create the index only if there are no duplicates (otherwise this just prints a notice).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM "User" WHERE email IS NOT NULL GROUP BY lower(email) HAVING count(*) > 1) THEN
        RAISE NOTICE 'Duplicate emails found (see step 1): user_email_lower_key NOT created.';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS user_email_lower_key ON "User" (lower(email));
        RAISE NOTICE 'user_email_lower_key is in place.';
    END IF;
END
$$;
