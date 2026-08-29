/*
package com.cypherlabs.benchmarks.microbenchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.sql.Array;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class StringInternBenchmark {
    private static ConcurrentHashMap<String,String> map;
    private static String[] strings;

    @Param({"1","10000"})
    private int nStrings;

    @Setup(Level.Iteration)
    public void setUp() {
        strings = new String[nStrings];
        for(int i = 0; i < nStrings; i++) {
            strings[i] = UUID.randomUUID().toString();
        }
        map = new ConcurrentHashMap<>();
    }

    @Benchmark
    public void testIntern(Blackhole bh) {
        for (int i = 0; i < nStrings; i++) {
            String t = strings[i].intern();
            bh.consume(t);
        }
    }

    @Benchmark
    public void testMap(Blackhole bh) {
        for (int i = 0; i < nStrings; i++) {
            String s = strings[i];
            String t = map.putIfAbsent(s, s);
            bh.consume(t);
        }
    }
}
*/
