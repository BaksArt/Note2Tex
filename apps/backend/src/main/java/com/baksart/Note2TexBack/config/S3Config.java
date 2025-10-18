package com.baksart.Note2TexBack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Bean
    @ConfigurationProperties(prefix = "note2tex.s3")
    public S3Props s3Props() { return new S3Props(); }

    @Bean
    public S3Client s3Client(S3Props p) {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(p.getAccessKey(), p.getSecretKey())))
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(p.getEndpoint()))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Props p) {
        return S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(p.getAccessKey(), p.getSecretKey())))
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(p.getEndpoint()))
                .build();
    }

    public static class S3Props {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket = "note2tex";
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }
}
