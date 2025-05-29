/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v5_0;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
class ElasticsearchRest5Test {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static ElasticsearchContainer elasticsearch;

  static HttpHost httpHost;

  static RestClient client;

  static ObjectMapper objectMapper;

  @BeforeAll
  static void setup() {
    if (!Boolean.getBoolean("testLatestDeps")) {
      elasticsearch =
          new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:5.6.16")
              .withEnv("xpack.ml.enabled", "false")
              .withEnv("xpack.security.enabled", "false");
    } else {
      elasticsearch =
          new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:6.8.16");
    }
    // limit memory usage
    elasticsearch.withEnv("ES_JAVA_OPTS", "-Xmx256m -Xms256m");
    elasticsearch.start();

    httpHost = HttpHost.create(elasticsearch.getHttpHostAddress());
    client =
        RestClient.builder(httpHost)
            .setMaxRetryTimeoutMillis(Integer.MAX_VALUE)
            .setRequestConfigCallback(
                builder ->
                    builder
                        .setConnectTimeout(Integer.MAX_VALUE)
                        .setSocketTimeout(Integer.MAX_VALUE))
            .build();

    objectMapper = new ObjectMapper();
  }

  @AfterAll
  static void cleanUp() {
    elasticsearch.stop();
  }

  @Test
  @SuppressWarnings("rawtypes")
  void elasticsearchStatus() throws IOException {
    Response response = client.performRequest("GET", "_cluster/health");

    Map result = objectMapper.readValue(response.getEntity().getContent(), Map.class);

    // usually this test reports green status, but sometimes it is yellow
    Assertions.assertTrue(
        "green".equals(result.get("status")) || "yellow".equals(result.get("status")));

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName("GET")
                    .hasKind(SpanKind.CLIENT)
                    .hasNoParent()
                    .hasAttributesSatisfyingExactly(
                        equalTo(maybeStable(DB_SYSTEM), "elasticsearch"),
                        equalTo(HTTP_REQUEST_METHOD, "GET"),
                        equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                        equalTo(SERVER_PORT, httpHost.getPort()),
                        equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health"));
              },
              span -> {
                span.hasName("GET")
                    .hasKind(SpanKind.CLIENT)
                    .hasParent(trace.getSpan(0))
                    .hasAttributesSatisfyingExactly(
                        equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                        equalTo(SERVER_PORT, httpHost.getPort()),
                        equalTo(HTTP_REQUEST_METHOD, "GET"),
                        equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                        equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health"),
                        equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
              });
        });
  }

  @Test
  @SuppressWarnings("rawtypes")
  void elasticsearchStatusAsync() throws Exception {
    Response[] requestResponse = {null};
    Exception[] exception = {null};
    CountDownLatch countDownLatch = new CountDownLatch(1);
    ResponseListener responseListener =
        new ResponseListener() {
          @Override
          public void onSuccess(Response response) {
            testing.runWithSpan(
                "callback",
                () -> {
                  requestResponse[0] = response;
                  countDownLatch.countDown();
                });
          }

          @Override
          public void onFailure(Exception e) {
            testing.runWithSpan(
                "callback",
                () -> {
                  exception[0] = e;
                  countDownLatch.countDown();
                });
          }
        };

    testing.runWithSpan(
        "parent",
        () -> {
          client.performRequestAsync("GET", "_cluster/health", responseListener);
        });
    countDownLatch.await();
    if (exception[0] != null) {
      throw exception[0];
    }
    Map result = objectMapper.readValue(requestResponse[0].getEntity().getContent(), Map.class);

    // usually this test reports green status, but sometimes it is yellow
    Assertions.assertTrue(
        "green".equals(result.get("status")) || "yellow".equals(result.get("status")));

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent();
              },
              span -> {
                span.hasName("GET")
                    .hasKind(SpanKind.CLIENT)
                    .hasParent(trace.getSpan(0))
                    .hasAttributesSatisfyingExactly(
                        equalTo(maybeStable(DB_SYSTEM), "elasticsearch"),
                        equalTo(HTTP_REQUEST_METHOD, "GET"),
                        equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                        equalTo(SERVER_PORT, httpHost.getPort()),
                        equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health"));
              },
              span -> {
                span.hasName("GET")
                    .hasKind(SpanKind.CLIENT)
                    .hasParent(trace.getSpan(1))
                    .hasAttributesSatisfyingExactly(
                        equalTo(SERVER_ADDRESS, httpHost.getHostName()),
                        equalTo(SERVER_PORT, httpHost.getPort()),
                        equalTo(HTTP_REQUEST_METHOD, "GET"),
                        equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                        equalTo(URL_FULL, httpHost.toURI() + "/_cluster/health"),
                        equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
              },
              span -> {
                span.hasName("callback").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0));
              });
        });
  }
}
