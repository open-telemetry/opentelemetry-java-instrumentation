/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.OPENSEARCH;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.ResponseListener;
import org.opensearch.client.RestClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractOpenSearchRestTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected OpensearchContainer opensearch;
  protected RestClient client;
  protected URI httpHost;

  protected abstract InstrumentationExtension getTesting();

  protected abstract RestClient buildRestClient(String hostAddress) throws Exception;

  protected abstract int getResponseStatus(Response response);

  protected abstract String getInstrumentationName();

  @BeforeAll
  void setUp() throws Exception {
    opensearch =
        new OpensearchContainer(DockerImageName.parse("opensearchproject/opensearch:1.3.6"))
            .withSecurityEnabled();
    cleanup.deferAfterAll(opensearch::stop);
    // limit memory usage and disable Log4j JMX to avoid cgroup detection issues in containers
    opensearch.withEnv(
        "OPENSEARCH_JAVA_OPTS",
        "-Xmx256m -Xms256m -Dlog4j2.disableJmx=true -Dlog4j2.disable.jmx=true -XX:-UseContainerSupport");
    opensearch.start();
    httpHost = URI.create(opensearch.getHttpHostAddress());

    client = buildRestClient(opensearch.getHttpHostAddress());
    cleanup.deferAfterAll(client);
  }

  @Test
  void shouldGetStatusWithTraces() throws IOException {
    Response response = client.performRequest(new Request("GET", "_cluster/health"));
    assertThat(getResponseStatus(response)).isEqualTo(200);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(maybeStable(DB_SYSTEM), OPENSEARCH),
                                equalTo(maybeStable(DB_OPERATION), "GET"),
                                equalTo(maybeStable(DB_STATEMENT), "GET _cluster/health"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort())),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort()),
                                equalTo(HTTP_REQUEST_METHOD, "GET"),
                                equalTo(maybeStablePeerService(), "test-peer-service"),
                                equalTo(URL_FULL, httpHost + "/_cluster/health"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L))));
  }

  @Test
  void shouldGetStatusAsyncWithTraces() throws Exception {
    AtomicReference<Response> requestResponse = new AtomicReference<>(null);
    AtomicReference<Exception> exception = new AtomicReference<>(null);
    CountDownLatch countDownLatch = new CountDownLatch(1);

    ResponseListener responseListener =
        new ResponseListener() {
          @Override
          public void onSuccess(Response response) {
            getTesting()
                .runWithSpan(
                    "callback",
                    () -> {
                      requestResponse.set(response);
                      countDownLatch.countDown();
                    });
          }

          @Override
          public void onFailure(Exception e) {
            getTesting()
                .runWithSpan(
                    "callback",
                    () -> {
                      exception.set(e);
                      countDownLatch.countDown();
                    });
          }
        };

    getTesting()
        .runWithSpan(
            "client",
            () -> {
              client.performRequestAsync(new Request("GET", "_cluster/health"), responseListener);
            });
    assertThat(countDownLatch.await(10, SECONDS)).isTrue();

    if (exception.get() != null) {
      throw exception.get();
    }
    assertThat(getResponseStatus(requestResponse.get())).isEqualTo(200);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("client").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(maybeStable(DB_SYSTEM), OPENSEARCH),
                                equalTo(maybeStable(DB_OPERATION), "GET"),
                                equalTo(maybeStable(DB_STATEMENT), "GET _cluster/health"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort())),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort()),
                                equalTo(HTTP_REQUEST_METHOD, "GET"),
                                equalTo(maybeStablePeerService(), "test-peer-service"),
                                equalTo(URL_FULL, httpHost + "/_cluster/health"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L)),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldOmitServerWithoutResponse() throws Exception {
    RestClient unavailableClient = buildRestClient("http://localhost:" + PortUtils.UNUSABLE_PORT);
    cleanup.deferCleanup(unavailableClient);

    assertThatThrownBy(
            () -> unavailableClient.performRequest(new Request("GET", "_cluster/health")))
        .isInstanceOf(IOException.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .satisfies(
                                spanData ->
                                    assertThat(spanData.getAttributes().asMap())
                                        .doesNotContainKeys(SERVER_ADDRESS, SERVER_PORT)),
                    span ->
                        span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldRecordServerOnErrorResponse() {
    assertThatThrownBy(() -> client.performRequest(new Request("GET", "_not_found")))
        .isInstanceOf(ResponseException.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttribute(equalTo(SERVER_ADDRESS, httpHost.getHost()))
                            .hasAttribute(equalTo(SERVER_PORT, httpHost.getPort())),
                    span ->
                        span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldRecordServerOnAsyncErrorResponse() throws InterruptedException {
    AtomicReference<Exception> exception = new AtomicReference<>(null);
    CountDownLatch countDownLatch = new CountDownLatch(1);

    ResponseListener responseListener =
        new ResponseListener() {
          @Override
          public void onSuccess(Response response) {
            countDownLatch.countDown();
          }

          @Override
          public void onFailure(Exception e) {
            exception.set(e);
            countDownLatch.countDown();
          }
        };

    client.performRequestAsync(new Request("GET", "_not_found"), responseListener);
    assertThat(countDownLatch.await(10, SECONDS)).isTrue();
    assertThat(exception.get()).isInstanceOf(ResponseException.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttribute(equalTo(SERVER_ADDRESS, httpHost.getHost()))
                            .hasAttribute(equalTo(SERVER_PORT, httpHost.getPort())),
                    span ->
                        span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldOmitServerAsyncWithoutResponse() throws Exception {
    RestClient unavailableClient = buildRestClient("http://localhost:" + PortUtils.UNUSABLE_PORT);
    cleanup.deferCleanup(unavailableClient);

    AtomicReference<Exception> exception = new AtomicReference<>(null);
    CountDownLatch countDownLatch = new CountDownLatch(1);

    ResponseListener responseListener =
        new ResponseListener() {
          @Override
          public void onSuccess(Response response) {
            countDownLatch.countDown();
          }

          @Override
          public void onFailure(Exception e) {
            exception.set(e);
            countDownLatch.countDown();
          }
        };

    unavailableClient.performRequestAsync(new Request("GET", "_cluster/health"), responseListener);
    assertThat(countDownLatch.await(10, SECONDS)).isTrue();
    assertThat(exception.get()).isInstanceOf(IOException.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .satisfies(
                                spanData ->
                                    assertThat(spanData.getAttributes().asMap())
                                        .doesNotContainKeys(SERVER_ADDRESS, SERVER_PORT)),
                    span ->
                        span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldRecordMetrics() throws IOException {
    Response response = client.performRequest(new Request("GET", "_cluster/health"));
    assertThat(getResponseStatus(response)).isEqualTo(200);

    getTesting().waitForTraces(1);

    assertDurationMetric(
        getTesting(),
        getInstrumentationName(),
        DB_OPERATION_NAME,
        DB_SYSTEM_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }
}
