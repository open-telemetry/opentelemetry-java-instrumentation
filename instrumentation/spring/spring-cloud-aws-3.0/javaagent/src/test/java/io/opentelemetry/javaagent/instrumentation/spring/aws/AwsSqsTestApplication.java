/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.aws;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.net.URI;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@SpringBootApplication
class AwsSqsTestApplication {
  static int sqsPort;
  static Consumer<String> messageHandler;

  @Bean
  SqsTemplate sqsTemplate(SqsAsyncClient sqsAsyncClient) {
    return SqsTemplate.builder().sqsAsyncClient(sqsAsyncClient).build();
  }

  @Bean
  SqsAsyncClient sqsAsyncClient() {
    return SqsAsyncClient.builder()
        .endpointOverride(URI.create("http://localhost:" + sqsPort))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("accessKey", "secretKey")))
        .region(Region.AP_NORTHEAST_1)
        .build();
  }

  @SqsListener("test-queue")
  void receiveStringMessage(String message) {
    if (messageHandler != null) {
      messageHandler.accept(message);
    }
  }
}
