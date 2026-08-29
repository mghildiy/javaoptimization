package com.cypherlabs.parquet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.crypto.FileEncryptionProperties;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.example.GroupWriteSupport;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;

import java.io.File;

public class AadPrefixFixtureGenerator {

    // Same key for all — matches what your other fixtures use
    private static final byte[] KEY = new byte[]{
            0x30,0x31,0x32,0x33,0x34,0x35,0x36,0x37,
            0x38,0x39,0x61,0x62,0x63,0x64,0x65,0x66,
            0x30,0x31,0x32,0x33,0x34,0x35,0x36,0x37,
            0x38,0x39,0x61,0x62,0x63,0x64,0x65,0x66
    }; // "0123456789abcdef0123456789abcdef"

    private static final byte[] AAD_PREFIX = "tenant-123".getBytes();

    private static final MessageType SCHEMA = MessageTypeParser.parseMessageType(
            "message schema { " +
                    "  required int64 id; " +
                    "  required binary name (UTF8); " +
                    "  required int64 salary; " +
                    "}"
    );

    public static void main(String[] args) throws Exception {
        generateAadPrefixStored("encrypt_columns_footer_aad_prefix_stored.parquet");
        generateAadPrefixSupplied("encrypt_columns_footer_aad_prefix_supplied.parquet");
        System.out.println("Done");
    }

    /// AAD prefix is set and stored in the file.
    /// Reader does not need to supply it — it reads it from the file.
    /// supplyAadPrefix = false
    private static void generateAadPrefixStored(String fileName) throws Exception {
        FileEncryptionProperties encryptionProperties = FileEncryptionProperties.builder(KEY)
                .withFooterKeyMetadata("footer_key".getBytes())
                .withAADPrefix(AAD_PREFIX)
                // storeAadPrefixInFile defaults to true — prefix is embedded in file
                .build();

        write(fileName, encryptionProperties);
        System.out.println("Written: " + fileName);
    }

    /// AAD prefix is set but NOT stored in the file.
    /// Reader must supply it via AadPrefixProvider.
    /// supplyAadPrefix = true
    private static void generateAadPrefixSupplied(String fileName) throws Exception {
        FileEncryptionProperties encryptionProperties = FileEncryptionProperties.builder(KEY)
                .withFooterKeyMetadata("footer_key".getBytes())
                .withAADPrefix(AAD_PREFIX)
                .withoutAADPrefixStorage() // sets supplyAadPrefix = true, does not store prefix
                .build();

        write(fileName, encryptionProperties);
        System.out.println("Written: " + fileName);
    }

    private static void write(String fileName, FileEncryptionProperties encryptionProperties) throws Exception {
        Configuration conf = new Configuration();
        GroupWriteSupport.setSchema(SCHEMA, conf);

        File outFile = new File(fileName);
        File parent = outFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        SimpleGroupFactory factory = new SimpleGroupFactory(SCHEMA);

        try (ParquetWriter<Group> writer = ExampleParquetWriter
                .builder(new org.apache.parquet.io.LocalOutputFile(outFile.toPath()))
                .withConf(conf)
                .withCompressionCodec(CompressionCodecName.UNCOMPRESSED)
                .withWriterVersion(ParquetProperties.WriterVersion.PARQUET_2_0)
                .withEncryption(encryptionProperties)
                .build()) {

            writer.write(factory.newGroup().append("id", 1L).append("name", "Mark").append("salary", 10001L));
            writer.write(factory.newGroup().append("id", 2L).append("name", "Mary").append("salary", 45345L));
            writer.write(factory.newGroup().append("id", 3L).append("name", "Mike").append("salary", 34345L));
        }
    }
}