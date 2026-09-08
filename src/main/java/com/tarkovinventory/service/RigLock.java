package com.tarkovinventory.service;

import java.util.HashMap;
import java.util.UUID;

public class RigLock {

    private static final HashMap<UUID, Long> LOCKS = new HashMap<>();

    private static final long LOCK_TIME = 150; // ms

    public static boolean tryLock(UUID id) {
        long now = System.currentTimeMillis();

        if (LOCKS.containsKey(id)) {
            long last = LOCKS.get(id);
            if (now - last < LOCK_TIME) {
                return false; // blocked spam
            }
        }

        LOCKS.put(id, now);
        return true;
    }
}
