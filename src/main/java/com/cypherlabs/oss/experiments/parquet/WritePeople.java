package com.cypherlabs.oss.experiments.parquet;

import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;

import java.nio.file.Path;

import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;

import java.nio.file.Path;

public class WritePeople {

    // turns hex-looking numbers like 0x15, 0x68 into a byte[]
    static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    static Group makeRow(SimpleGroupFactory factory, String name, int age,
                         String city, int zip, byte[] metadata, byte[] value) {
        Group row = factory.newGroup();
        row.append("name", name);
        row.append("age", age);

        Group address = row.addGroup("address");
        address.append("city", city);
        address.append("zip", zip);

        Group extra = row.addGroup("extra");
        extra.append("metadata", Binary.fromConstantByteArray(metadata));
        extra.append("value", Binary.fromConstantByteArray(value));

        return row;
    }

    public static void main(String[] args) throws Exception {
        MessageType schema = MessageTypeParser.parseMessageType(
                "message person {\n" +
                        "  required binary name (STRING);\n" +
                        "  required int32 age;\n" +
                        "  required group address {\n" +
                        "    required binary city (STRING);\n" +
                        "    required int32 zip;\n" +
                        "  }\n" +
                        "  optional group extra {\n" +
                        "    required binary metadata;\n" +
                        "    required binary value;\n" +
                        "  }\n" +
                        "}");

        SimpleGroupFactory factory = new SimpleGroupFactory(schema);

        // no field names anywhere, so the dictionary is empty
        byte[] emptyMetadata = bytes(0x01, 0x00, 0x00);

        try (ParquetWriter<Group> writer = ExampleParquetWriter
                .builder(new LocalOutputFile(Path.of("people.parquet")))
                .withType(schema)
                .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                .build()) {

            // "hello"
            writer.write(makeRow(factory, "ann", 31, "Pune", 411001,
                    emptyMetadata,
                    bytes(0x15, 0x68, 0x65, 0x6C, 0x6C, 0x6F)));

            // 42
            writer.write(makeRow(factory, "bob", 28, "Mumbai", 400001,
                    emptyMetadata,
                    bytes(0x0C, 0x2A)));

            // { "tags": ["x", "y"] }
            writer.write(makeRow(factory, "cat", 45, "Delhi", 110001,
                    bytes(0x11, 0x01, 0x00, 0x04, 0x74, 0x61, 0x67, 0x73),
                    bytes(0x02, 0x01, 0x00, 0x00, 0x09,
                            0x03, 0x02, 0x00, 0x02, 0x04, 0x05, 0x78, 0x05, 0x79)));
        }
    }
}