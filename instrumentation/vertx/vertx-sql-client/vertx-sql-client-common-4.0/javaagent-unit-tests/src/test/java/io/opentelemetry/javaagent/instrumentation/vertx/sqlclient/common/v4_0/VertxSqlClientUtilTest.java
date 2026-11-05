/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.vertx.core.Promise;
import io.vertx.sqlclient.SqlConnectOptions;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VertxSqlClientUtilTest {

  private static final Instrumenter<VertxSqlClientRequest, Void> INSTRUMENTER =
      Instrumenter.<VertxSqlClientRequest, Void>builder(
              OpenTelemetry.noop(), "test", request -> request.getQueryText())
          .buildInstrumenter();

  @Test
  void endsSuccessfulRequestOnlyOnce() {
    Promise<Object> promise = Promise.promise();
    VertxSqlClientUtil.attachRequest(promise, request("success"), Context.root(), Context.root());

    Scope scope = VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, promise, null);

    assertThat(scope).isNotNull();
    scope.close();
    assertThat(VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, promise, null)).isNull();
  }

  @Test
  void endsFailedRequestOnlyOnce() {
    Promise<Object> promise = Promise.promise();
    VertxSqlClientUtil.attachRequest(promise, request("failure"), Context.root(), Context.root());
    IllegalStateException error = new IllegalStateException("failure");

    Scope scope = VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, promise, error);

    assertThat(scope).isNotNull();
    scope.close();
    assertThat(VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, promise, null)).isNull();
  }

  @Test
  void freezesRequestBeforeEndingSpan() {
    Promise<Object> promise = Promise.promise();
    VertxSqlClientDeferredRequest request =
        new VertxSqlClientDeferredRequest(
            "freeze", VertxSqlClientInfo.createUnknown("postgresql"), false, null);
    VertxSqlClientUtil.attachRequest(promise, request, Context.root(), Context.root());

    Scope scope = VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, promise, null);

    assertThat(scope).isNotNull();
    scope.close();
    assertThat(
            request.replaceInfo(
                VertxSqlClientInfo.create(
                    new SqlConnectOptions().setHost("db.example").setPort(5432), "postgresql")))
        .isFalse();
    assertThat(request.getConfiguredServerAddress()).isNull();
  }

  @Test
  void updatesSpanNameAfterCapturingInfo() {
    Promise<Object> promise = Promise.promise();
    VertxSqlClientDeferredRequest request =
        new VertxSqlClientDeferredRequest(
            "", VertxSqlClientInfo.createUnknown("postgresql"), false, null);
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    try (SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build()) {
      OpenTelemetrySdk openTelemetry =
          OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
      Instrumenter<VertxSqlClientRequest, Void> instrumenter =
          Instrumenter.<VertxSqlClientRequest, Void>builder(
                  openTelemetry,
                  "test",
                  DbClientSpanNameExtractor.create(new VertxSqlClientAttributesGetter()))
              .buildInstrumenter();
      Context context = instrumenter.start(Context.root(), request);
      assertThat(
              request.replaceInfo(
                  VertxSqlClientInfo.create(
                      new SqlConnectOptions()
                          .setHost("db.example")
                          .setPort(5432)
                          .setDatabase("database"),
                      "postgresql")))
          .isTrue();
      VertxSqlClientUtil.attachRequest(promise, request, context, Context.root());

      Scope scope = VertxSqlClientUtil.endQuerySpan(instrumenter, promise, null);

      assertThat(scope).isNotNull();
      scope.close();
      assertThat(exporter.getFinishedSpanItems())
          .singleElement()
          .satisfies(
              spanData -> assertThat(spanData).hasName("database").hasTotalAttributeCount(0));
      assertThat(request.replaceInfo(VertxSqlClientInfo.createUnknown("postgresql"))).isFalse();
    }
  }

  @Test
  void endsRequestWhenSpanNameUpdateFails() {
    Promise<Object> promise = Promise.promise();
    RenameFailingRequest request = new RenameFailingRequest("rename failure");
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    try (SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build()) {
      OpenTelemetrySdk openTelemetry =
          OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
      Instrumenter<VertxSqlClientRequest, Void> instrumenter =
          Instrumenter.<VertxSqlClientRequest, Void>builder(
                  openTelemetry, "test", VertxSqlClientRequest::getQueryText)
              .buildInstrumenter();
      Context context = instrumenter.start(Context.root(), request);
      assertThat(
              request.replaceInfo(
                  VertxSqlClientInfo.create(
                      new SqlConnectOptions().setHost("db.example").setPort(5432), "postgresql")))
          .isTrue();
      request.failSpanNameExtraction = true;
      IllegalStateException applicationError = new IllegalStateException("application failure");
      VertxSqlClientUtil.attachRequest(promise, request, context, Context.root());

      Scope parentScope = VertxSqlClientUtil.endQuerySpan(instrumenter, promise, applicationError);

      assertThat(parentScope).isNotNull();
      parentScope.close();
      assertThat(request.spanNameExtractionFailures).hasValue(1);
      assertThat(exporter.getFinishedSpanItems())
          .singleElement()
          .satisfies(
              spanData ->
                  assertThat(spanData)
                      .hasStatus(StatusData.error())
                      .hasException(applicationError)
                      .hasTotalAttributeCount(0));
      assertThat(VertxSqlClientUtil.endQuerySpan(instrumenter, promise, null)).isNull();
      assertThat(exporter.getFinishedSpanItems()).hasSize(1);
    }
  }

  @Test
  void claimsRequestOnlyOnceWhenCallbacksRace() throws Exception {
    Promise<Object> promise = Promise.promise();
    VertxSqlClientUtil.attachRequest(promise, request("race"), Context.root(), Context.root());
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> first =
          executor.submit(
              () -> {
                barrier.await();
                Scope scope = VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, promise, null);
                if (scope != null) {
                  scope.close();
                }
                return scope != null;
              });
      Future<Boolean> second =
          executor.submit(
              () -> {
                barrier.await();
                Scope scope =
                    VertxSqlClientUtil.endQuerySpan(
                        INSTRUMENTER, promise, new IllegalStateException("duplicate"));
                if (scope != null) {
                  scope.close();
                }
                return scope != null;
              });

      assertThat(first.get()).isNotEqualTo(second.get());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void keepsDistinctPromisesIndependent() {
    Promise<Object> firstPromise = Promise.promise();
    Promise<Object> secondPromise = Promise.promise();
    VertxSqlClientUtil.attachRequest(
        firstPromise, request("first"), Context.root(), Context.root());
    VertxSqlClientUtil.attachRequest(
        secondPromise, request("second"), Context.root(), Context.root());

    Scope firstScope = VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, firstPromise, null);
    Scope secondScope =
        VertxSqlClientUtil.endQuerySpan(
            INSTRUMENTER, secondPromise, new IllegalStateException("second"));

    assertThat(firstScope).isNotNull();
    assertThat(secondScope).isNotNull();
    firstScope.close();
    secondScope.close();
    assertThat(VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, firstPromise, null)).isNull();
    assertThat(VertxSqlClientUtil.endQuerySpan(INSTRUMENTER, secondPromise, null)).isNull();
  }

  private static VertxSqlClientRequest request(String query) {
    return new VertxSqlClientRequest(
        query, VertxSqlClientInfo.create(new SqlConnectOptions(), "postgresql"), false, null);
  }

  private static final class RenameFailingRequest extends VertxSqlClientDeferredRequest {
    private final AtomicInteger spanNameExtractionFailures = new AtomicInteger();
    private boolean failSpanNameExtraction;

    private RenameFailingRequest(String query) {
      super(query, VertxSqlClientInfo.createUnknown("postgresql"), false, null);
    }

    @Override
    public String getQueryText() {
      if (failSpanNameExtraction) {
        spanNameExtractionFailures.incrementAndGet();
        throw new IllegalStateException("span name extraction failure");
      }
      return super.getQueryText();
    }
  }
}
