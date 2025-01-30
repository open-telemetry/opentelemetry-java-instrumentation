/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractJaxRsHttpServerTest<SERVER> extends AbstractHttpServerTest<SERVER> {

  protected abstract void awaitBarrier(int amount, TimeUnit timeUnit) throws Exception;

  protected boolean testInterfaceMethodWithPath() {
    return true;
  }

  protected boolean asyncCancelHasSendError() {
    return false;
  }

  protected boolean shouldTestCompletableStageAsync() {
    return Boolean.getBoolean("testLatestDeps");
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setHasHandlerSpan(endpoint -> true);
    options.setTestNotFound(false);
    options.setHasResponseCustomizer(endpoint -> true);
  }

  @Test
  void superMethodWithoutPathAnnotation() {
    AggregatedHttpResponse response =
        client.get(address.resolve("test-resource-super").toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(SUCCESS.getBody());

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET " + getContextPath() + "/test-resource-super")
                            .hasKind(SpanKind.SERVER)
                            .hasNoParent(),
                    span ->
                        span.hasName("controller")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  void interfaceMethodWithPathAnnotation() {
    assumeTrue(testInterfaceMethodWithPath());

    AggregatedHttpResponse response =
        client.get(address.resolve("test-resource-interface/call").toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(SUCCESS.getBody());

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET " + getContextPath() + "/test-resource-interface/call")
                            .hasKind(SpanKind.SERVER)
                            .hasNoParent(),
                    span ->
                        span.hasName("controller")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  void subResourceLocator() {
    AggregatedHttpResponse response =
        client
            .get(address.resolve("test-sub-resource-locator/call/sub").toString())
            .aggregate()
            .join();

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(SUCCESS.getBody());

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(
                                "GET " + getContextPath() + "/test-sub-resource-locator/call/sub")
                            .hasKind(SpanKind.SERVER)
                            .hasNoParent(),
                    span ->
                        span.hasName("JaxRsSubResourceLocatorTestResource.call")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("controller")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("SubResource.call")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("controller")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(3))));
  }

  enum AsyncResponseTestKind {
    SUCCESSFUL("succeed", 200) {
      @Override
      void assertBody(String body) {
        assertThat(body).isEqualTo("success");
      }
    },
    FAILING("throw", 500) {
      @Override
      void assertBody(String body) {
        assertThat(body).isEqualTo("failure");
      }
    },
    CANCELED("cancel", 503) {
      @Override
      void assertBody(String body) {
        assertThat(body).isNotNull();
      }
    };

    final String action;
    final int statusCode;

    AsyncResponseTestKind(String action, int statusCode) {
      this.action = action;
      this.statusCode = statusCode;
    }

    abstract void assertBody(String body);
  }

  @ParameterizedTest
  @EnumSource(AsyncResponseTestKind.class)
  void shouldHandleAsyncResponse(AsyncResponseTestKind testKind) throws Exception {
    String url = address.resolve("async?action=" + testKind.action).toString();
    CompletableFuture<AggregatedHttpResponse> futureResponse = client.get(url).aggregate();

    // there are no traces yet
    assertThat(testing().getExportedSpans()).isEmpty();

    // barrier is released and resource class sends response
    awaitBarrier(10, SECONDS);

    AggregatedHttpResponse response = futureResponse.join();

    assertThat(response.status().code()).isEqualTo(testKind.statusCode);
    testKind.assertBody(response.contentUtf8());

    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> assertions =
                  new ArrayList<>(
                      Arrays.asList(
                          span ->
                              assertServerSpan(
                                  span,
                                  "GET",
                                  new ServerEndpoint(
                                      "async",
                                      "async?action=" + testKind.action,
                                      testKind.statusCode,
                                      null,
                                      false),
                                  testKind.statusCode),
                          span -> {
                            span.hasName("JaxRsTestResource.asyncOp")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParent(trace.getSpan(0))
                                .hasAttributesSatisfyingExactly(
                                    satisfies(
                                        CODE_NAMESPACE, name -> name.endsWith("JaxRsTestResource")),
                                    equalTo(CODE_FUNCTION, "asyncOp"),
                                    equalTo(
                                        AttributeKey.booleanKey("jaxrs.canceled"),
                                        testKind == AsyncResponseTestKind.CANCELED ? true : null));
                            if (testKind == AsyncResponseTestKind.FAILING) {
                              span.hasStatus(StatusData.error())
                                  .hasException(new IllegalStateException("failure"));
                            }
                          }));
              if (asyncCancelHasSendError() && testKind == AsyncResponseTestKind.CANCELED) {
                assertions.add(
                    span ->
                        span.satisfies(
                                spanData -> assertThat(spanData.getName()).endsWith("sendError"))
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1)));
              }
              trace.hasSpansSatisfyingExactly(assertions);
            });
  }

  enum CompletionStageTestKind {
    SUCCESSFUL("succeed", 200) {
      @Override
      void assertBody(String body) {
        assertThat(body).isEqualTo("success");
      }
    },
    FAILING("throw", 500) {
      @Override
      void assertBody(String body) {
        assertThat(body).isEqualTo("failure");
      }
    };

    final String action;
    final int statusCode;

    CompletionStageTestKind(String action, int statusCode) {
      this.action = action;
      this.statusCode = statusCode;
    }

    abstract void assertBody(String body);
  }

  @ParameterizedTest
  @EnumSource(CompletionStageTestKind.class)
  void shouldHandleCompletionStage(CompletionStageTestKind testKind) throws Exception {
    // JAX-RS 2.1+ only
    assumeTrue(shouldTestCompletableStageAsync());

    String url = address.resolve("async-completion-stage?action=" + testKind.action).toString();
    CompletableFuture<AggregatedHttpResponse> futureResponse = client.get(url).aggregate();

    // there are no traces yet
    assertThat(testing().getExportedSpans()).isEmpty();

    // barrier is released and resource class sends response
    awaitBarrier(10, SECONDS);

    AggregatedHttpResponse response = futureResponse.join();

    assertThat(response.status().code()).isEqualTo(testKind.statusCode);
    testKind.assertBody(response.contentUtf8());

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        assertServerSpan(
                            span,
                            "GET",
                            new ServerEndpoint(
                                "async",
                                "async-completion-stage?action=" + testKind.action,
                                testKind.statusCode,
                                null,
                                false),
                            testKind.statusCode),
                    span -> {
                      span.hasName("JaxRsTestResource.jaxRs21Async")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(
                                  CODE_NAMESPACE,
                                  "io.opentelemetry.instrumentation.jaxrs.v2_0.test.JaxRsTestResource"),
                              equalTo(CODE_FUNCTION, "jaxRs21Async"));
                      if (testKind == CompletionStageTestKind.FAILING) {
                        span.hasStatus(StatusData.error())
                            .hasException(new IllegalStateException("failure"));
                      }
                    }));
  }

  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String methodName = endpoint.name().toLowerCase(Locale.ROOT);
    return span.hasName("JaxRsTestResource." + methodName)
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfyingExactly(
            satisfies(CODE_NAMESPACE, name -> name.endsWith("JaxRsTestResource")),
            equalTo(CODE_FUNCTION, methodName));
  }
}
