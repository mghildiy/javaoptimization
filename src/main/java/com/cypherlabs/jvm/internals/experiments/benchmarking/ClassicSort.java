package com.cypherlabs.jvm.internals.experiments.benchmarking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

public class ClassicSort {

    private static final int N = 1_000;
    private static final int I = 150_000;

    private static final List<Integer> TEST_DATA = new ArrayList<>();

    public static void main(String[] args) {
        RandomGenerator rg = new Random();
        for(int i = 0; i < N; i++) {
            TEST_DATA.add(rg.nextInt(Integer.MAX_VALUE));
        }

        double startTime = System.nanoTime();
        for(int i = 0; i < I; i++) {
            List<Integer> copy = new ArrayList<Integer>(TEST_DATA);
            List<Integer> x = copy.stream().map(y -> y*y).toList();
            Collections.sort(copy);
        }
        double endTime = System.nanoTime();
        double timePerOperation = (endTime - startTime) / (1_000_000_000L * I);
        System.out.println("Result: " + (1 / timePerOperation) +
                " op/s");
    }
}

