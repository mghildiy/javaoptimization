package com.cypherlabs.simd;

public class ArrayAddition {

    static final int SIZE = 8_192;
    static final int REPEATS = 200_000;

    public static void main(String[] args) {

        int[] a = new int[SIZE];
        int[] b = new int[SIZE];
        int[] c = new int[SIZE];

        for (int i = 0; i < SIZE; i++) {
            a[i] = i;
            b[i] = i * 2;
        }

        // Warm up
        for (int j = 0; j < 10_000; j++) {
            add(a, b, c);
        }

        long[] times = new long[REPEATS];

        for (int j = 0; j < REPEATS; j++) {
            long start = System.nanoTime();
            add(a, b, c);
            long end = System.nanoTime();
            times[j] = end - start;
        }

        long total = 0;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long t : times) {
            total += t;
            if (t < min) min = t;
            if (t > max) max = t;
        }

        System.out.println("Average: " + (total / (double) REPEATS) / 1_000_000.0 + " ms");
        System.out.println("Min: " + min / 1_000_000.0 + " ms");
        System.out.println("Max: " + max / 1_000_000.0 + " ms");

        // Print only the last 10 runs, as a spot-check
        System.out.println("Last 10 runs:");
        for (int j = REPEATS - 10; j < REPEATS; j++) {
            System.out.println("Run " + j + ": " + times[j] / 1_000_000.0 + " ms");
        }

        System.out.println("Result check: " + c[SIZE - 1]);
    }

    static void add(int[] a, int[] b, int[] c) {
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }
}