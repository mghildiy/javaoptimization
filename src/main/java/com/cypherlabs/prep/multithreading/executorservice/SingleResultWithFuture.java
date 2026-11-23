package com.cypherlabs.prep.multithreading.executorservice;

import java.util.concurrent.*;

public class SingleResultWithFuture {

    public static void main(String[] args) {
        try(ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> task = () -> {
                System.out.println("Task running on " + Thread.currentThread().getName());
                Thread.sleep(10000);
                return 4;
            };

            Future<Integer> fut = pool.submit(task);
            // current thread ie main thread wait inside get..if some other thread calls interrupt on main thread, then get would throw InterruptedException
            int result = fut.get();
            System.out.println("Result:"+ result);
            pool.shutdown(); // not really needed, as try-with-resources would call close(), which calls shutdown() internally
            System.out.println("All done");
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
