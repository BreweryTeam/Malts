package dev.jsinco.malts.utility;

import org.bukkit.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

// Schedules tasks on an entity's thread through the server's internal entity scheduler, so it can be used during shutdown
public final class InternalScheduler {

    private static final Field TASK_SCHEDULER;
    private static final Method SCHEDULE;

    static {
        Field field = null;
        Method method = null;
        try {
            Class<?> craftEntity = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
            field = craftEntity.getField("taskScheduler");
            // boolean schedule(Consumer<? extends Entity> run, Consumer<? extends Entity> retired, long delay)
            method = field.getType().getMethod("schedule", Consumer.class, Consumer.class, long.class);
            if (method.getReturnType() != boolean.class) {
                method = null;
            }
        } catch (Throwable throwable) {
            Text.debug("Internal entity scheduler is not available: " + throwable);
        }
        TASK_SCHEDULER = method != null ? field : null;
        SCHEDULE = method;
    }

    private InternalScheduler() {
    }

    public static boolean schedule(Entity entity, Runnable task, Runnable retired) {
        if (SCHEDULE == null) {
            return false;
        }
        try {
            Object scheduler = TASK_SCHEDULER.get(entity);
            if (scheduler == null) {
                return false;
            }
            Consumer<Object> run = ignored -> task.run();
            Consumer<Object> retire = ignored -> retired.run();
            return (boolean) SCHEDULE.invoke(scheduler, run, retire, 1L);
        } catch (Throwable throwable) {
            Text.debug("Could not use the internal entity scheduler: " + throwable);
            return false;
        }
    }
}
