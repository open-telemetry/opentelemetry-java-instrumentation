/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.interceptor.MessageInterceptor;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@SpringBootApplication
class AwsSqsTestApplication {
  static int sqsPort;
  static volatile Consumer<Message<String>> messageHandler;
  static volatile Consumer<List<String>> batchMessageHandler;
  static volatile Consumer<Collection<Message<Object>>> batchInterceptor;

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

  // sees the messages of a batch as they are passed to the listener pipeline, before payload
  // conversion for the listener method creates new message instances
  @Bean
  MessageInterceptor<Object> batchMessageInterceptor() {
    return new MessageInterceptor<>() {
      @Override
      public Collection<Message<Object>> intercept(Collection<Message<Object>> messages) {
        if (batchInterceptor != null) {
          batchInterceptor.accept(messages);
        }
        return messages;
      }
    };
  }

  @SqsListener("test-queue")
  void receiveStringMessage(Message<String> message) {
    if (messageHandler != null) {
      messageHandler.accept(message);
    }
  }

  @SqsListener("batch-queue")
  void receiveBatch(List<String> messages) {
    if (batchMessageHandler != null) {
      batchMessageHandler.accept(messages);
    }
  }
}
