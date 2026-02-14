package dev.jsinco.malts.utility;

import dev.jsinco.malts.Malts;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Executors {

    private static final Malts INSTANCE = Malts.getInstance();
    public static final boolean IS_FOLIA = ClassUtil.classExists("io.papermc.paper.threadedregions.RegionizedServer");
    private static final long MIN_DELAY = IS_FOLIA ? 1 : 0;

    public static ScheduledTask runRepeatingAsync(long delay, long period, TimeUnit timeUnit, Consumer<ScheduledTask> consumer) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(INSTANCE, consumer, delay, period, timeUnit);
    }

    public static ScheduledTask runDelayedAsync(long delay, TimeUnit timeUnit, Consumer<ScheduledTask> consumer) {
        return Bukkit.getAsyncScheduler().runDelayed(INSTANCE, consumer, delay, timeUnit);
    }

    public static ScheduledTask runRepeatingAsync(long period, TimeUnit timeUnit, Consumer<ScheduledTask> consumer) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(INSTANCE, consumer, 0, period, timeUnit);
    }

    public static ScheduledTask runAsync(Consumer<ScheduledTask> consumer) {
        return Bukkit.getAsyncScheduler().runNow(INSTANCE, consumer);
    }


    // CompletableFuture

    // TODO: Better logging
    public static <U> CompletableFuture<U> supplyAsyncWithSQLException(ExceptionUtil.ThrowingSQLExceptionWithReturn<U> supplier) {
        if (!Malts.isShutdown()) {
            return CompletableFuture.supplyAsync(() -> ExceptionUtil.runWithSQLExceptionHandling(supplier)).exceptionally(throwable -> {
                throwable.printStackTrace();
                return null;
            });
        } else {
            return CompletableFuture.completedFuture(ExceptionUtil.runWithSQLExceptionHandling(supplier));
        }
    }

    // TODO: Better logging
    public static <U> CompletableFuture<U> supplyAsyncWithSQLException(ExceptionUtil.ThrowingSQLExceptionWithReturn<U> supplier, Executor executor) {
        if (!Malts.isShutdown()) {
            return CompletableFuture.supplyAsync(() -> ExceptionUtil.runWithSQLExceptionHandling(supplier), executor).exceptionally(throwable -> {
                throwable.printStackTrace();
                return null;
            });
        } else {
            return CompletableFuture.completedFuture(ExceptionUtil.runWithSQLExceptionHandling(supplier));
        }
    }

    // Synchronous

    public static ScheduledTask delayedSync(Location location, long delay, Runnable runnable) {
        return Bukkit.getRegionScheduler().runDelayed(INSTANCE, location, t -> runnable.run(), Math.min(delay, MIN_DELAY));
    }
    public static ScheduledTask delayedSync(Entity entity, long delay, Runnable runnable) {
        return entity.getScheduler().runDelayed(INSTANCE, t -> runnable.run(), null, Math.min(delay, MIN_DELAY));
    }

    public static ScheduledTask delayedSync(long delay, Runnable runnable) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(INSTANCE, (t) -> runnable.run(), Math.min(delay, MIN_DELAY));
    }

    public static ScheduledTask sync(Location location, Runnable runnable) {
        return Bukkit.getRegionScheduler().run(INSTANCE, location, (task) -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                Text.error("An error occurred while running a synchronous task for location " + location, t);
            }
        });
    }

    public static ScheduledTask sync(Entity entity, Runnable runnable) {
        return entity.getScheduler().run(INSTANCE, (task) -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                Text.error("An error occurred while running a synchronous task for entity " + entity.getUniqueId(), t);
            }
        }, null);
    }

    public static void runSync(Location location, Runnable runnable) {
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            runnable.run();
        } else {
            sync(location, runnable);
        }
    }

    public static void runSync(Entity entity, Runnable runnable) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            runnable.run();
        } else {
            sync(entity, runnable);
        }
    }

    public static ExecutorService newSingleThreadExecutor() {
        return new ThreadPoolExecutor(
                1,
                1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    //thread.setName("SQLite-SingleThread");
                    thread.setDaemon(true);
                    thread.setContextClassLoader(Malts.class.getClassLoader());
                    return thread;
                }
        );
    }

}
