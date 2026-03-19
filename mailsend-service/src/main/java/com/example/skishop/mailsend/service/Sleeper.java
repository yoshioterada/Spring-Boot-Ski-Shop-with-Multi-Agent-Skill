package com.example.skishop.mailsend.service;

public interface Sleeper {

    void sleep(long millis) throws InterruptedException;

    static Sleeper system() {
        return Thread::sleep;
    }
}
