/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static java.util.Arrays.asList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RedissonInstrumenterFactoryTest {

  @ParameterizedTest
  @ValueSource(longs = {0, 1})
  void atomicBatchOmitsNamespaceFromSpanNameButRetainsAttribute(long databaseIndex) {
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    try (OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build())
            .build()) {
      GlobalOpenTelemetry.resetForTest();
      GlobalOpenTelemetry.set(openTelemetry);
      Instrumenter<RedissonBatchRequest, Void> instrumenter =
          RedissonInstrumenterFactory.createBatchInstrumenter("test-redisson");
      RedissonBatchRequest request =
          RedissonBatchRequest.create(
              asList("SET", "SET"), asList("SET first ?", "SET second ?"), databaseIndex);

      Context context = instrumenter.start(Context.root(), request);
      instrumenter.end(context, request, null, null);

      assertThat(exporter.getFinishedSpanItems())
          .singleElement()
          .satisfies(
              span ->
                  assertThat(span)
                      .hasName("MULTI SET")
                      .hasKind(SpanKind.CLIENT)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(
                          equalTo(DB_SYSTEM_NAME, "redis"),
                          equalTo(DB_NAMESPACE, String.valueOf(databaseIndex)),
                          equalTo(DB_OPERATION_NAME, "MULTI SET"),
                          equalTo(DB_OPERATION_BATCH_SIZE, 2L),
                          equalTo(DB_QUERY_TEXT, "SET first ?; SET second ?")));
    } finally {
      GlobalOpenTelemetry.resetForTest();
    }
  }
}
