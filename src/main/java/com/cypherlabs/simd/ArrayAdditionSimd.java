package com.cypherlabs.simd;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorSpecies;

public class ArrayAdditionSimd {

    static final int SIZE = 8_192;
    static final int REPEATS = 200_000;

    static final VectorSpecies<Integer> SPECIES =
            IntVector.SPECIES_PREFERRED;

    public static void main(String[] args) {

        System.out.println("Vector length: " + SPECIES.length() + " (species: " + SPECIES + ")");

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

        System.out.println("Last 10 runs:");
        for (int j = REPEATS - 10; j < REPEATS; j++) {
            System.out.println("Run " + j + ": " + times[j] / 1_000_000.0 + " ms");
        }

        System.out.println("Result check: " + c[SIZE - 1]);
    }

    static void add(int[] a, int[] b, int[] c) {

        int i = 0;
        int upperBound = SPECIES.loopBound(a.length);

        for (; i < upperBound; i += SPECIES.length()) {
            IntVector va = IntVector.fromArray(SPECIES, a, i);
            IntVector vb = IntVector.fromArray(SPECIES, b, i);
            IntVector vc = va.add(vb);
            vc.intoArray(c, i);
        }

        for (; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }
}