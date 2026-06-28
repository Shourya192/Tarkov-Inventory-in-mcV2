package com.tarkovinventory.service;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class RigTransaction {

    private boolean committed = false;

    private final List<Runnable> operations = new ArrayList<>();

    public void add(Runnable op) {
        operations.add(op);
    }

    public void commit() {
        if (committed) return;
        committed = true;

        for (Runnable op : operations) {
            op.run();
        }
    }

    public void rollback() {
        operations.clear();
    }
}
