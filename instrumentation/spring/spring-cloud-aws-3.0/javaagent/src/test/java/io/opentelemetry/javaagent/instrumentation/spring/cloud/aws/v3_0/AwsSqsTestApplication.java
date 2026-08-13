/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.ListenerMode;
import io.awspring.cloud.sqs.listener.interceptor.MessageInterceptor;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.net.URI;
import java.time.Duration;
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
  private static final ThreadLocal<Span> ambientBatchSpan = new ThreadLocal<>();
  private static final ThreadLocal<Scope> ambientBatchScope = new ThreadLocal<>();

  static int sqsPort;
  static volatile Consumer<String> messageHandler;
  static volatile Consumer<List<String>> batchMessageHandler;
  static volatile boolean addAmbientBatchSpan;

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

  @Bean
  SqsMessageListenerContainerFactory<Object> batchFactory(
      SqsAsyncClient sqsAsyncClient, MessageInterceptor<Object> batchAmbientSpanInterceptor) {
    return SqsMessageListenerContainerFactory.builder()
        .configure(
            options ->
                options
                    .listenerMode(ListenerMode.BATCH)
                    .maxMessagesPerPoll(10)
                    .pollTimeout(Duration.ofSeconds(2)))
        .sqsAsyncClient(sqsAsyncClient)
        .messageInterceptor(batchAmbientSpanInterceptor)
        .build();
  }

  @Bean
  MessageInterceptor<Object> batchAmbientSpanInterceptor() {
    return new MessageInterceptor<>() {
      @Override
      public Collection<Message<Object>> intercept(Collection<Message<Object>> messages) {
        if (addAmbientBatchSpan) {
          Span ambientSpan =
              GlobalOpenTelemetry.getTracer("test").spanBuilder("ambient").startSpan();
          ambientBatchSpan.set(ambientSpan);
          ambientBatchScope.set(ambientSpan.makeCurrent());
        }
        return messages;
      }

      @Override
      public void afterProcessing(Collection<Message<Object>> messages, Throwable throwable) {
        Scope ambientScope = ambientBatchScope.get();
        if (ambientScope != null) {
          ambientScope.close();
          ambientBatchSpan.get().end();
          ambientBatchScope.remove();
          ambientBatchSpan.remove();
        }
      }
    };
  }

  @Bean
  SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(
      SqsAsyncClient sqsAsyncClient) {
    return SqsMessageListenerContainerFactory.builder().sqsAsyncClient(sqsAsyncClient).build();
  }

  @SqsListener(value = "test-queue", factory = "defaultSqsListenerContainerFactory")
  void receiveStringMessage(String message) {
    if (messageHandler != null) {
      messageHandler.accept(message);
    }
  }

  @SqsListener(value = "test-batch-queue", factory = "batchFactory", id = "batchContainer")
  void receiveBatchMessages(List<String> messages) {
    if (batchMessageHandler != null) {
      batchMessageHandler.accept(messages);
    }
  }
}
