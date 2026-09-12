package com.cypherlabs.jvm.internals.experiments;

import java.util.ArrayList;
import java.util.List;

public class GCDemoWithEscapeAnalysis {
    static long sink;
    static byte[] escapedRef; // holds the ARRAY'S IDENTITY, not just a value from it

    public static void main(String[] args) {
        List<byte[]> survivors = new ArrayList<>();
        long iteration = 0;
        while (iteration < 2_000_000) {
            byte[] junk = new byte[1024];
            junk[0] = (byte) iteration;
            escapedRef = junk;       // the object itself now escapes the loop iteration
            sink += escapedRef[0];

            if (iteration % 5000 == 0) {
                survivors.add(new byte[1024]);
            }
            iteration++;
        }
        System.out.println("Done. Survivors held: " + survivors.size() + ", sink=" + sink);
    }
}
