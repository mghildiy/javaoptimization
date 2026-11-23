package com.cypherlabs.prep.multithreading.executorservice;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BoundedPoolOverload {

    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, // core pool size
                4, // max pool size
                30, TimeUnit.SECONDS, // keep-alive time for extra workers out of core pool
                new ArrayBlockingQueue<>(3), // task queue, no  more than 3 tasks in queuw
                new ThreadPoolExecutor.AbortPolicy() // reject any task with exception if queue is full and maz pool size is reached
        );

        for(int i=1; i <= 10; i++) {
            int id =i;
            try {
                pool.execute(() -> {
                    System.out.println("Task:" + id + " running on " + Thread.currentThread().getName());
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

                System.out.println("Accepted task " + id
                        + " | workers=" + pool.getPoolSize()
                        + " queued=" + pool.getQueue().size());
            } catch(RejectedExecutionException e) {
                System.out.println("Rejected task:"+ id);
            }
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
    }
}
