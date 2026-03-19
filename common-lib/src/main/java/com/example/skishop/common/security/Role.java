package com.example.skishop.common.security;

public enum Role {
    ADMIN,
    MANAGER,
    STAFF,
    EMPLOYEE,
    USER,
    CUSTOMER;

    public String authority() {
        return "ROLE_" + this.name();
    }
}
