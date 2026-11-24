package com.cypherlabs.prep.multithreading.executorservice;

import java.util.concurrent.*;

public class ScheduledTasksDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // 1. run once, after a 2 second delay
        scheduler.schedule(
                () -> System.out.println("One-time task"),
                2, TimeUnit.SECONDS);

        // 2. run repeatedly: first after 1 s, then every 3 s
        ScheduledFuture<?> repeating = scheduler.scheduleAtFixedRate(
                () -> System.out.println("Tick at " + System.currentTimeMillis() / 1000),
                1, 3, TimeUnit.SECONDS);

        Thread.sleep(10_000);          // let it run for 10 seconds

        repeating.cancel(false);       // stop the repeating task
        scheduler.shutdown();
    }
}
