package com.cypherlabs.simd;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorSpecies;

public class ArrayAdditionSimd {

    static final int SIZE = 8_192;
    static final int REPEATS = 200_000;

    static final VectorSpecies<Integer> SPECIES =
            IntVector.SPECIES_PREFERRED;

    public static void main(String[] args) {

        int[] a = new int[SIZE];
        int[] b = new int[SIZE];
        int[] c = new int[SIZE];

        for (int i = 0; i < SIZE; i++) {
            a[i] = i;
            b[i] = i * 2;
        }

        // Warm up
        for (int j = 0; j < 10; j++) {
            add(a, b, c);
        }

        // Measure
        long total = 0;

        for (int j = 0; j < REPEATS; j++) {

            long start = System.nanoTime();

            add(a, b, c);

            long end = System.nanoTime();

            long time = end - start;
            total += time;

            System.out.println(
                    "Run " + j + ": " +
                            time / 1_000_000.0 + " ms"
            );
        }

        System.out.println(
                "Average: " +
                        (total / 20) / 1_000_000.0 + " ms"
        );

        System.out.println(
                "Result check: " + c[SIZE - 1]
        );
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

        // Remaining elements
        for (; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }
}