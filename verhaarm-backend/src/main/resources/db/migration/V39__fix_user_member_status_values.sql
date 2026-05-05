UPDATE users
SET member_status = 'SCHUELERFUX'
WHERE member_status = 'MILITAERFUX';

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_member_status;

ALTER TABLE users
    ADD CONSTRAINT chk_users_member_status
        CHECK (member_status IN (
                                 'FUX',
                                 'SCHUELERFUX',
                                 'KONKNEIPANT',
                                 'BURSCH',
                                 'INAKTIVER',
                                 'PHILISTER'
            ));