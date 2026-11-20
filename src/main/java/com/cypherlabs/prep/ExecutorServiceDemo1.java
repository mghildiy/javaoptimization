package com.cypherlabs.prep;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExecutorServiceDemo1 {

    public static void main(String[] args) {
        try(ExecutorService pool = Executors.newFixedThreadPool(3);) {
            for(int i = 0; i < 10; i++) {
                final int taksId = i;
                pool.execute(() -> {
                    System.out.println("Task:"+ taksId + " running on " + Thread.currentThread().getName());
                    try { Thread.sleep(500); } catch (InterruptedException e) { }
                });
            }

            // marks state of executor service as SHUT, no new tasks accepted, running tasks continue to run, tasks in queue stay
            // so that workers waiting on new tasks(take() on queue) check if status is SHUT and no task in queue, and exit
            pool.shutdown();

            // block for 10 seconds so that all tasks are completed, else they complete after exit of main(worker threads in executor service are non-daemon threads,
            // so JVM waits for them to exit)
            pool.awaitTermination(10, TimeUnit.SECONDS);

            System.out.println("All done");
        } catch(InterruptedException ie) {

        }
    }
}
