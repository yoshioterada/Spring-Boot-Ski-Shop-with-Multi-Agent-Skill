package com.example.skishop.authentication.exception;

import java.time.LocalDateTime;

public class AccountLockedException extends AuthenticationException {
    private final LocalDateTime unlockAt;

    public AccountLockedException(LocalDateTime unlockAt) {
        super("アカウントがロックされています。ロック解除時刻: " + unlockAt);
        this.unlockAt = unlockAt;
    }

    public LocalDateTime getUnlockAt() { return unlockAt; }
}
