-- INACTIVE ステータスを user_profiles の CHECK 制約に追加
ALTER TABLE user_profiles
    DROP CONSTRAINT ck_user_profiles_status;

ALTER TABLE user_profiles
    ADD CONSTRAINT ck_user_profiles_status
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'DEACTIVATED'));
