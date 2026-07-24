package com.szu.afternoon3.platform.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldStoreAndClearAllCurrentThreadFields() {
        UserContext.setUserId(42L);
        UserContext.setNickname("Elaine");
        UserContext.setRole("ADMIN");

        assertThat(UserContext.getUserId()).isEqualTo(42L);
        assertThat(UserContext.getNickname()).isEqualTo("Elaine");
        assertThat(UserContext.getRole()).isEqualTo("ADMIN");

        UserContext.clear();

        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getNickname()).isNull();
        assertThat(UserContext.getRole()).isNull();
    }

    @Test
    void shouldKeepValuesIsolatedBetweenThreads() throws InterruptedException {
        UserContext.setUserId(42L);
        AtomicReference<Long> childValue = new AtomicReference<>();

        Thread child = new Thread(() -> {
            childValue.set(UserContext.getUserId());
            UserContext.setUserId(7L);
            UserContext.clear();
        });
        child.start();
        child.join();

        assertThat(childValue.get()).isNull();
        assertThat(UserContext.getUserId()).isEqualTo(42L);
    }
}
