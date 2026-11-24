package com.ethanpierce.cursedtech.core;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny server-side scheduler for multi-tick abilities. */
public final class Tasks {
    private Tasks() {}

    @FunctionalInterface
    public interface Task {
        /** @param age ticks since start. Return true when finished. */
        boolean tick(ServerLevel level, int age);
    }

    private record Running(ServerLevel level, Task task, int[] age) {}

    private static final List<Running> RUNNING = new ArrayList<>();
    private static final List<Running> PENDING = new ArrayList<>();

    public static void start(ServerLevel level, Task task) {
        PENDING.add(new Running(level, task, new int[]{0}));
    }

    public static void tickAll() {
        RUNNING.addAll(PENDING);
        PENDING.clear();
        Iterator<Running> it = RUNNING.iterator();
        while (it.hasNext()) {
            Running r = it.next();
            boolean done;
            try {
                done = r.task.tick(r.level, r.age[0]++);
            } catch (Exception e) {
                done = true;
            }
            if (done || r.age[0] > 20 * 60) it.remove();
        }
    }

    public static void clear() {
        RUNNING.clear();
        PENDING.clear();
    }
}
