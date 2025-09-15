/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.ClientResources;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LettuceShouldStartTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Container
  static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.7-alpine").withExposedPorts(6379);

  static RedisClient redisClient;
  static String redisUri;

  @BeforeAll
  static void setUp() {
    redisUri = "redis://" + redisServer.getHost() + ":" + redisServer.getFirstMappedPort() + "/0";
  }

  @AfterAll
  static void cleanUp() {
    if (redisClient != null) {
      redisClient.shutdown();
    }
  }

  @Test
  void shouldNotCreateSpansWhenInstrumentationDisabled() {
    // Create OpenTelemetry instance
    InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
    
    OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();

    // Create LettuceTelemetry with instrumentation disabled
    LettuceTelemetry lettuceTelemetry = LettuceTelemetry.builder(openTelemetry)
        .setInstrumentationEnabled(false)
        .build();

    redisClient = RedisClient.create(
        ClientResources.builder()
            .tracing(lettuceTelemetry.newTracing())
            .build(),
        redisUri);

    RedisCommands<String, String> sync = redisClient.connect().sync();

    // Perform a Redis operation
    sync.set("test-key", "test-value");
    String value = sync.get("test-key");
    
    assertThat(value).isEqualTo("test-value");

    // Verify no spans were created due to disabled instrumentation
    assertThat(spanExporter.getFinishedSpanItems()).isEmpty();
  }

  @Test
  void shouldCreateSpansWhenInstrumentationEnabled() {
    LettuceTelemetry lettuceTelemetry = LettuceTelemetry.create(testing.getOpenTelemetry());

    redisClient = RedisClient.create(
        ClientResources.builder()
            .tracing(lettuceTelemetry.newTracing())
            .build(),
        redisUri);

    RedisCommands<String, String> sync = redisClient.connect().sync();

    // Clear any existing spans
    testing.clearData();

    // Perform a Redis operation
    sync.set("test-key-2", "test-value-2");

    // Verify spans were created
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(
            span -> span.hasName("SET").hasKind(SpanKind.CLIENT)));
  }
}