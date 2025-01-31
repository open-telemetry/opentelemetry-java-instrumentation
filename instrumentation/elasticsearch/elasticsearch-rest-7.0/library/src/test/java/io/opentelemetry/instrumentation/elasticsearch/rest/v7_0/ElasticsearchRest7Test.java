/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.v7_0;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("deprecation") // using deprecated semconv
class ElasticsearchRest7Test {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static ElasticsearchContainer elasticsearch;

  static HttpHost httpHost;

  static RestClient client;

  static ObjectMapper objectMapper;

  @BeforeAll
  static void setUp() {
    elasticsearch =
        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.10.2");
    // limit memory usage
    elasticsearch.withEnv("ES_JAVA_OPTS", "-Xmx256m -Xms256m");
    elasticsearch.start();

    httpHost = HttpHost.create(elasticsearch.getHttpHostAddress());

    client =
        RestClient.builder(httpHost)
            .setRequestConfigCallback(
                builder ->
                    builder
                        .setConnectTimeout(Integer.MAX_VALUE)
                        .setSocketTimeout(Integer.MAX_VALUE))
            .build();
    client = ElasticsearchRest7Telemetry.create(testing.getOpenTelemetry()).wrap(client);

    objectMapper = new ObjectMapper();
  }

  @AfterAll
  static void cleanUp() {
    elasticsearch.stop();
  }

  @Test
  public void elasticsearchStatus() throws Exception {
    Response response = client.performRequest(new Request("GET", "_cluster/health"));
    Map<?, ?> result = objectMapper.readValue(response.getEntity().getContent(), Map.class);
    Assertions.assertEquals(result.get("status"), "green");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "elasticsearch"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                            equalTo(SERVER_PORT, httpHost.getPort()),
                            equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health"))));
  }

  @Test
  public void elasticsearchStatusAsync() throws Exception {
    AsyncRequest asyncRequest = new AsyncRequest();
    CountDownLatch countDownLatch = new CountDownLatch(1);
    ResponseListener responseListener =
        new ResponseListener() {
          @Override
          public void onSuccess(Response response) {

            runWithSpan(
                "callback",
                () -> {
                  asyncRequest.setRequestResponse(response);
                  countDownLatch.countDown();
                });
          }

          @Override
          public void onFailure(Exception e) {
            runWithSpan(
                "callback",
                () -> {
                  asyncRequest.setException(e);
                  countDownLatch.countDown();
                });
          }
        };

    runWithSpan(
        "parent",
        () -> client.performRequestAsync(new Request("GET", "_cluster/health"), responseListener));
    //noinspection ResultOfMethodCallIgnored
    countDownLatch.await(10, TimeUnit.SECONDS);

    if (asyncRequest.getException() != null) {
      throw asyncRequest.getException();
    }

    Map<?, ?> result =
        objectMapper.readValue(
            asyncRequest.getRequestResponse().getEntity().getContent(), Map.class);
    Assertions.assertEquals(result.get("status"), "green");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "elasticsearch"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                            equalTo(SERVER_PORT, httpHost.getPort()),
                            equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  private static class AsyncRequest {
    volatile Response requestResponse = null;
    volatile Exception exception = null;

    public Response getRequestResponse() {
      return requestResponse;
    }

    public void setRequestResponse(Response requestResponse) {
      this.requestResponse = requestResponse;
    }

    public Exception getException() {
      return exception;
    }

    public void setException(Exception exception) {
      this.exception = exception;
    }
  }
}
