/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
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
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
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

    /*
    def spanCount = 2
    def hasSendError = asyncCancelHasSendError() && action == "cancel"
    if (hasSendError) {
      spanCount++
    }
    assertTraces(1) {
      trace(0, spanCount) {
        asyncServerSpan(it, 0, url, statusCode)
        handlerSpan(it, 1, span(0), "asyncOp", isCancelled, isError, errorMessage)
        if (hasSendError) {
          sendErrorSpan(it, 2, span(1))
        }
      }
    }
     */

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
                                        CodeIncubatingAttributes.CODE_NAMESPACE,
                                        name -> name.endsWith("JaxRsTestResource")),
                                    equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "asyncOp"),
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

  /*

  @Unroll
  def "should handle #desc AsyncResponse"() {
    given:
    def url = address.resolve("async?action=${action}").toString()

    when: "async call is started"
    def futureResponse = client.get(url).aggregate()

    then: "there are no traces yet"
    assertTraces(0) {
    }

    when: "barrier is released and resource class sends response"
    awaitBarrier(10, SECONDS)
    def response = futureResponse.join()

    then:
    response.status().code() == statusCode
    bodyPredicate(response.contentUtf8())

    def spanCount = 2
    def hasSendError = asyncCancelHasSendError() && action == "cancel"
    if (hasSendError) {
      spanCount++
    }
    assertTraces(1) {
      trace(0, spanCount) {
        asyncServerSpan(it, 0, url, statusCode)
        handlerSpan(it, 1, span(0), "asyncOp", isCancelled, isError, errorMessage)
        if (hasSendError) {
          sendErrorSpan(it, 2, span(1))
        }
      }
    }

    where:
    desc         | action    | statusCode | bodyPredicate            | isCancelled | isError | errorMessage
    "successful" | "succeed" | 200        | { it == "success" }      | false       | false   | null
    "failing"    | "throw"   | 500        | { it == "failure" }      | false       | true    | "failure"
    "canceled"   | "cancel"  | 503        | { it instanceof String } | true        | false   | null
  }
  */

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
                                  CodeIncubatingAttributes.CODE_NAMESPACE,
                                  "io.opentelemetry.instrumentation.jaxrs.v2_0.test.JaxRsTestResource"),
                              equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "jaxRs21Async"));
                      if (testKind == CompletionStageTestKind.FAILING) {
                        span.hasStatus(StatusData.error())
                            .hasException(new IllegalStateException("failure"));
                      }
                    }));
  }

  /*
    @Unroll
    def "should handle #desc CompletionStage (JAX-RS 2.1+ only)"() {
      assumeTrue(shouldTestCompletableStageAsync())
      given:
      def url = address.resolve("async-completion-stage?action=${action}").toString()

      when: "async call is started"
      def futureResponse = client.get(url).aggregate()

      then: "there are no traces yet"
      assertTraces(0) {
      }

      when: "barrier is released and resource class sends response"
      awaitBarrier(10, SECONDS)
      def response = futureResponse.join()

      then:
      response.status().code() == statusCode
      bodyPredicate(response.contentUtf8())

      assertTraces(1) {
        trace(0, 2) {
          asyncServerSpan(it, 0, url, statusCode)
          handlerSpan(it, 1, span(0), "jaxRs21Async", false, isError, errorMessage)
        }
      }

      where:
      desc         | action    | statusCode | bodyPredicate       | isError | errorMessage
      "successful" | "succeed" | 200        | { it == "success" } | false   | null
      "failing"    | "throw"   | 500        | { it == "failure" } | true    | "failure"
    }

    @Override
    boolean hasHandlerSpan(ServerEndpoint endpoint) {
      true
    }

    @Override
    boolean testNotFound() {
      false
    }

    @Override
    boolean testPathParam() {
      true
    }

    boolean testInterfaceMethodWithPath() {
      true
    }

    boolean asyncCancelHasSendError() {
      false
    }

    boolean shouldTestCompletableStageAsync() {
      Boolean.getBoolean("testLatestDeps")
    }

    @Override
    boolean hasResponseCustomizer(ServerEndpoint endpoint) {
      true
    }

    @Override
    void serverSpan(TraceAssert trace,
        int index,
        String traceID = null,
        String parentID = null,
        String method = "GET",
        ServerEndpoint endpoint = SUCCESS,
        String spanID = null) {
      serverSpan(trace, index, traceID, parentID, spanID, method,
          endpoint == PATH_PARAM ? getContextPath() + "/path/{id}/param" : endpoint.resolvePath(address).path,
          endpoint.resolve(address),
          endpoint.status,
          endpoint.query)
    }

    void asyncServerSpan(TraceAssert trace,
        int index,
        String url,
        int statusCode) {
      def rawUrl = URI.create(url).toURL()
      serverSpan(trace, index, null, null, null, "GET",
          rawUrl.path,
          rawUrl.toURI(),
          statusCode,
          null)
    }

    void serverSpan(TraceAssert trace,
        int index,
        String traceID,
        String parentID,
        String spanID,
        String method,
        String path,
        URI fullUrl,
        int statusCode,
        String query) {
      trace.span(index) {
        name method + " " + path
        kind SERVER
        if (statusCode >= 500) {
          status ERROR
        }
        if (traceID != null) {
          traceId traceID
        }
        if (parentID != null) {
          parentSpanId parentID
        } else {
          hasNoParent()
        }
        if (spanID != null) {
          spanId spanID
        }
        attributes {
          "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
          "$ServerAttributes.SERVER_ADDRESS" fullUrl.host
          "$ServerAttributes.SERVER_PORT" fullUrl.port
          "$NetworkAttributes.NETWORK_PEER_ADDRESS" "127.0.0.1"
          "$NetworkAttributes.NETWORK_PEER_PORT" Long
          "$UrlAttributes.URL_SCHEME" fullUrl.getScheme()
          "$UrlAttributes.URL_PATH" fullUrl.getPath()
          "$UrlAttributes.URL_QUERY" fullUrl.getQuery()
          "$HttpAttributes.HTTP_REQUEST_METHOD" method
          "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" statusCode
          "$UserAgentAttributes.USER_AGENT_ORIGINAL" TEST_USER_AGENT
          "$ClientAttributes.CLIENT_ADDRESS" TEST_CLIENT_IP
          // Optional
          "$HttpAttributes.HTTP_ROUTE" path
          if (fullUrl.getPath().endsWith(ServerEndpoint.CAPTURE_HEADERS.getPath())) {
            "http.request.header.x-test-request" { it == ["test"] }
            "http.response.header.x-test-response" { it == ["test"] }
          }
          if (statusCode >= 500) {
            "$ErrorAttributes.ERROR_TYPE" "$statusCode"
          }
        }
      }
    }

    @Override
    void handlerSpan(TraceAssert trace,
        int index,
        Object parent,
        String method = "GET",
        ServerEndpoint endpoint = SUCCESS) {
      handlerSpan(trace, index, parent,
          endpoint.name().toLowerCase(),
          false,
          endpoint == EXCEPTION,
          EXCEPTION.body)
    }

    void handlerSpan(TraceAssert trace,
        int index,
        Object parent,
        String methodName,
        boolean isCancelled,
        boolean isError,
        String exceptionMessage = null) {
      trace.span(index) {
        name "JaxRsTestResource.${methodName}"
        kind INTERNAL
        if (isError) {
          status ERROR
          errorEvent(Exception, exceptionMessage)
        }
        childOf((SpanData) parent)
        attributes {
          "$CodeIncubatingAttributes.CODE_NAMESPACE" "test.JaxRsTestResource"
          "$CodeIncubatingAttributes.CODE_FUNCTION" methodName
          if (isCancelled) {
            "jaxrs.canceled" true
          }
        }
      }
    }
  */

  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String methodName = endpoint.name().toLowerCase(Locale.ROOT);
    return span.hasName("JaxRsTestResource." + methodName)
        .hasKind(SpanKind.INTERNAL)
        .hasAttributesSatisfyingExactly(
            satisfies(
                CodeIncubatingAttributes.CODE_NAMESPACE,
                name -> name.endsWith("JaxRsTestResource")),
            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, methodName));
  }
}
