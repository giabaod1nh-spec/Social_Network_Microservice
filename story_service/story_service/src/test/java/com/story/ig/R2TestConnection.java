package com.story.ig;

import com.mongodb.assertions.Assertions;
import com.story.ig.config.R2Config;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@SpringBootTest
class R2TestConnection {

    @Autowired
    private S3Client r2Client;

    @Autowired
    private R2Config r2Config;

    @Test
    void testListBuckets() {
        ListBucketsResponse response = r2Client.listBuckets();
        response.buckets().forEach(b -> System.out.println("Bucket: " + b.name()));
        Assertions.assertNotNull(response);
    }

    @Test
    void testUploadDummyFile() throws Exception {
        String key = "test/hello.txt";

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Config.getBucketName())
                .key(key)
                .contentType("text/plain")
                .build();

        r2Client.putObject(request,
                RequestBody.fromString("Hello R2 from Spring Boot!"));

        System.out.println("Upload success: " + r2Config.getPublicUrl() + "/" + key);
    }
}