package com.cypherlabs.prep.multithreading.executorservice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class TaskWithMultipleFutures {

    public static void main(String[] args) {
        try(ExecutorService pool = Executors.newFixedThreadPool(3)) {
            List<Future<Integer>> futs = new ArrayList<>();
            for(int i = 1; i <= 5; i++) {
                int id = i;
                futs.add(pool.submit(() -> {
                    Thread.sleep(1000);
                    return id * 10;
                }));
            }

            int total = 0;
            for(Future<Integer> fut : futs) {
                total += fut.get();
            }

            pool.shutdown();
            System.out.println("Sum:"+ total);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
