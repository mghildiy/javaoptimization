package com.cypherlabs.modern.streams;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamDemo {

    record Employee(String name, String dept, int age, double salary, String city) {}

    public static void main(String[] args) {
        List<String> names = List.of("India", "America", "Argentina", "China", "Cuba", "Russia", "Germany");

        Stream<String> str = names.stream();
        Stream<String> bigNames = str.filter(StreamDemo::bigName);
        Stream<String> upperCaseNames = bigNames.map(StreamDemo::upperCaseName);
        List<String> bigUpperCaseCountryNames = upperCaseNames.toList();
        System.out.println(bigUpperCaseCountryNames);

        List<Employee> emps = List.of(
                new Employee("Asha",  "Eng",   30, 120000, "Mumbai"),
                new Employee("Ravi",  "Eng",   45, 150000, "Pune"),
                new Employee("Karan", "Sales", 35,  70000, "Delhi"),
                new Employee("Sana",  "Sales", 26,  60000, "Mumbai"),
                new Employee("Divya", "HR",    32,  75000, "Delhi")
        );

        List<String> mumbaiEmpNames = emps.stream()
                .filter(emp -> emp.city.equals("Mumbai"))
                .map(emp -> emp.name)
                .toList();
        System.out.println(mumbaiEmpNames);
        List<String> highEarningEmpNames = emps.stream()
                .filter(emp -> emp.salary > 100000)
                .map(emp -> emp.name)
                .toList();
        System.out.println(highEarningEmpNames);
        Set<String> distinctCities = emps.stream()
                .map(emp -> emp.city)
                .collect(Collectors.toSet());
        System.out.println(distinctCities);
        String commaSeparatedNames = emps.stream()
                .map(emp -> emp.name)
                .collect(Collectors.joining(", "));
        System.out.println(commaSeparatedNames);

        double totalSalary = emps.stream()
                .map(emp -> emp.salary)
                .reduce(0.0, (totalSoFar, sal) -> totalSoFar + sal);
        System.out.println(totalSalary);

        double avgAge = emps.stream()
                .mapToInt(emp -> emp.age)
                .average()
                .orElse(0);
        System.out.println(avgAge);

        Employee highesPaidEmpl = emps.stream()
                .max(StreamDemo::compareBySalary)
                .orElseThrow(() -> new IllegalArgumentException("No employee found"));
        System.out.println(highesPaidEmpl);

        long olderThan30 = emps.stream()
                .filter(emp -> emp.age > 30)
                        .count();
        System.out.println(olderThan30);

        boolean isAnyEmpOlderThan40 = emps.stream()
                .anyMatch(emp -> emp.age > 40);
        System.out.println(isAnyEmpOlderThan40);

        boolean doAllEmpsEarnMorThan50k = emps.stream()
                .allMatch(emp -> emp.salary > 50000);
        System.out.println(doAllEmpsEarnMorThan50k);

        Employee firstEmplInSales = emps.stream()
                .filter(emp -> emp.dept.equals("Sales"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No sales employee found"));
        System.out.println(firstEmplInSales);

    }

    private static int compareBySalary(Employee emp1, Employee emp2) {
        if(emp1.salary == emp2.salary) return 0;
        if(emp1.salary > emp2.salary)
            return 1;
        else
            return -1;
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
