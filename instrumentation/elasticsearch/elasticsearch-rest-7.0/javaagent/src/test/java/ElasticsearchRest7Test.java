/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import groovy.json.JsonSlurper;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.assertj.core.api.AbstractLongAssert;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

public class ElasticsearchRest7Test {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static ElasticsearchContainer elasticsearch;

  static HttpHost httpHost;

  static RestClient client;

  @BeforeAll
  static void setUp() {
    elasticsearch = new ElasticsearchContainer(
        "docker.elastic.co/elasticsearch/elasticsearch-oss:7.10.2");
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
  }

  @AfterAll
  static void cleanUp() {
    elasticsearch.stop();
  }

  public void elasticsearchStatus() throws Exception {
    Response response = client.performRequest(new Request("GET", "_cluster/health"));
    Object result = new JsonSlurper().parseText(EntityUtils.toString(response.getEntity()));
    Assertions.assertInstanceOf(Map.class, result);
    Assertions.assertEquals(((Map) result).get("status"), "green");

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(
            span -> span.hasName("GET")
                .hasKind(SpanKind.CLIENT)
                .hasNoParent()
                .hasAttributesSatisfyingExactly(
                    equalTo(SemanticAttributes.DB_SYSTEM, "elasticsearch"),
                    equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                    equalTo(
                        SemanticAttributes.HTTP_URL, httpHost.toURI() + "/_cluster/health")
                ),
            span -> span.hasName("GET")
                .hasKind(SpanKind.CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                    equalTo(SemanticAttributes.NET_PEER_NAME, httpHost.getHostName()),
                    equalTo(SemanticAttributes.NET_PEER_PORT, httpHost.getPort()),
                    equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                    equalTo(AttributeKey.stringKey("net.protocol.name"), "http"),
                    equalTo(AttributeKey.stringKey("net.protocol.version"), "1.1"),
                    equalTo(SemanticAttributes.HTTP_URL, httpHost.toURI() + "/_cluster/health"),
                    equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                    satisfies(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                        AbstractLongAssert::isPositive)
                )
        ));
  }

  @Test
  // ignore deprecation interface
  public void elasticsearchStatusAsync() throws Exception {
    AsyncRequest asyncRequest = new AsyncRequest();
    CountDownLatch countDownLatch = new CountDownLatch(1);
    ResponseListener responseListener = new ResponseListener() {
      @Override
      public void onSuccess(Response response) {

        runWithSpan("callback", () -> {
          asyncRequest.setRequestResponse(response);
          countDownLatch.countDown();
        });
      }

      @Override
      public void onFailure(Exception e) {
        runWithSpan("callback", () -> {
          asyncRequest.setException(e);
          countDownLatch.countDown();
        });
      }
    };

    runWithSpan("parent", () ->
        client.performRequestAsync(new Request("GET", "_cluster/health"), responseListener)
    );
    countDownLatch.await(10, TimeUnit.SECONDS);

    if (asyncRequest.getException() != null) {
      throw asyncRequest.getException();
    }
    Object result = new JsonSlurper().parseText(
        EntityUtils.toString(asyncRequest.getRequestResponse().getEntity()));
    Assertions.assertInstanceOf(Map.class, result);
    Assertions.assertEquals(((Map) result).get("status"), "green");

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent")
                .hasKind(SpanKind.INTERNAL)
                .hasNoParent(),
            span -> span.hasName("GET")
                .hasKind(SpanKind.CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                    equalTo(SemanticAttributes.DB_SYSTEM, "elasticsearch"),
                    equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                    equalTo(
                        SemanticAttributes.HTTP_URL, httpHost.toURI() + "/_cluster/health")
                ),
            span -> span.hasName("GET")
                .hasKind(SpanKind.CLIENT)
                .hasParent(trace.getSpan(1))
                .hasAttributesSatisfyingExactly(
                    equalTo(SemanticAttributes.NET_PEER_NAME, httpHost.getHostName()),
                    equalTo(SemanticAttributes.NET_PEER_PORT, httpHost.getPort()),
                    equalTo(SemanticAttributes.HTTP_METHOD, "GET"),
                    equalTo(AttributeKey.stringKey("net.protocol.name"), "http"),
                    equalTo(AttributeKey.stringKey("net.protocol.version"), "1.1"),
                    equalTo(SemanticAttributes.HTTP_URL, httpHost.toURI() + "/_cluster/health"),
                    equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200L),
                    satisfies(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                        AbstractLongAssert::isPositive)
                ),
            span -> span.hasName("callback")
                .hasKind(SpanKind.INTERNAL)
                .hasParent(trace.getSpan(0))
        ));
  }

  private static class AsyncRequest {
    Response requestResponse = null;
    Exception exception = null;

    public Response getRequestResponse() {
      return requestResponse;
    }

    public AsyncRequest setRequestResponse(Response requestResponse) {
      this.requestResponse = requestResponse;
      return this;
    }

    public Exception getException() {
      return exception;
    }

    public AsyncRequest setException(Exception exception) {
      this.exception = exception;
      return this;
    }
  }
}
