package com.cypherlabs.oss.experiments.parquet;

import org.apache.hadoop.fs.Path;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.example.GroupReadSupport;

import org.apache.hadoop.fs.Path;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.example.GroupReadSupport;

public class ReadPeople {

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X ", x));
        return sb.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        try (ParquetReader<Group> reader = ParquetReader
                .builder(new GroupReadSupport(), new Path("people.parquet"))
                .build()) {

            Group row;
            while ((row = reader.read()) != null) {
                String name = row.getString("name", 0);
                int age = row.getInteger("age", 0);

                Group address = row.getGroup("address", 0);
                String city = address.getString("city", 0);
                int zip = address.getInteger("zip", 0);

                System.out.println(name + ", " + age + ", " + city + ", " + zip);

                Group extra = row.getGroup("extra", 0);
                byte[] metadata = extra.getBinary("metadata", 0).getBytes();
                byte[] value = extra.getBinary("value", 0).getBytes();
                System.out.println("  metadata: " + hex(metadata));
                System.out.println("  value:    " + hex(value));
            }
        }
    }
}