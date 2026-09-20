package com.cypherlabs.modern.streams;

import java.util.List;
import java.util.stream.Stream;

public class StreamDemo {

    public static void main(String[] args) {
        List<String> names = List.of("India", "America", "Argentina", "China", "Cuba", "Russia", "Germany");

        Stream<String> str = names.stream();
        Stream<String> bigNames = str.filter(StreamDemo::bigName);
        Stream<String> upperCaseNames = bigNames.map(StreamDemo::upperCaseName);
        List<String> bigUpperCaseCountryNames = upperCaseNames.toList();
        System.out.println(bigUpperCaseCountryNames);
    }

    static private boolean bigName(String name) {
        System.out.println("Checking big name filter for:"+ name);
        return name.length() >= 7;
    }

    static private String upperCaseName(String name) {
        System.out.println("Applying uppercase map for:"+ name);
        return name.toUpperCase();
    }
}
