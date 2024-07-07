/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twitterutilstats.v23_11;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.CONNECTION_TIMEOUT;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.READ_TIMEOUT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import com.twitter.finagle.Http;
import com.twitter.finagle.ListeningServer;
import com.twitter.finagle.Service;
import com.twitter.finagle.http.Method;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.service.RetryBudget;
import com.twitter.finagle.stats.Counter;
import com.twitter.finagle.stats.CustomUnit;
import com.twitter.finagle.stats.DefaultStatsReceiver$;
import com.twitter.finagle.stats.MetricBuilder;
import com.twitter.util.Await;
import com.twitter.util.Duration;
import com.twitter.util.Future;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.DoublePointAssert;
import io.opentelemetry.sdk.testing.assertj.LongPointAssert;
import io.opentelemetry.testing.internal.armeria.internal.shaded.guava.collect.ImmutableMap;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ClientTest {
  private static final Logger logger = Logger.getLogger(ClientTest.class.getName());

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static Request buildRequest(String method, URI uri, Map<String, String> headers) {
    Request request =
        Request.apply(
            Method.apply(method.toUpperCase(Locale.ENGLISH)),
            uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getRawQuery()));
    request.host(uri.getHost() + ":" + safePort(uri));
    headers.forEach((key, value) -> request.headerMap().put(key, value));
    return request;
  }

  static int safePort(URI uri) {
    int port = uri.getPort();
    if (port == -1) {
      port = uri.getScheme().equals("https") ? 443 : 80;
    }
    return port;
  }

  @Test
  void sometest() throws Exception {
    Service<Request, Response> service =
        new Service<Request, Response>() {
          final Counter counter =
              DefaultStatsReceiver$.MODULE$
                  .scope("mine")
                  .counter(
                      MetricBuilder.forCounter()
                          .withName("requests2")
                          .withUnits(new CustomUnit("widgets")));

          @Override
          public Future<Response> apply(Request request) {
            counter.incr();
            Response response = Response.apply();
            response.setContentString("Hello, World!");
            return Future.value(response);
          }
        };

    ListeningServer serve =
        Http.server()
            .withLabel("http_server")
            .withStatsReceiver(DefaultStatsReceiver$.MODULE$)
            .serve(":8080", service);

    Service<Request, Response> client =
        Http.client()
            .withNoHttp2()
            .withTransport()
            .readTimeout(Duration.fromMilliseconds(READ_TIMEOUT.toMillis()))
            .withTransport()
            .connectTimeout(Duration.fromMilliseconds(CONNECTION_TIMEOUT.toMillis()))
            // disable automatic retries for sanity and result predictability/uniformity
            .withRetryBudget(RetryBudget.Empty())
            .newService("127.0.0.1:8080");

    IntStream.range(0, 10)
        .forEach(
            ignored -> {
              Future<Response> response =
                  client.apply(
                      buildRequest("GET", URI.create("http://localhost:8080/"), ImmutableMap.of()));

              try {
                Await.result(response);
                Thread.sleep(250L);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    Await.result(client.close(Duration.fromMilliseconds(1000L)));
    Await.result(serve.close(Duration.fromMilliseconds(1000L)));

    TestStatsReceiver.getInstance()
        .getCounters()
        .asMap()
        .forEach(
            (name, counters) -> {
              List<Consumer<LongPointAssert>> collect =
                  counters.stream()
                      .filter(
                          counter -> {
                            if (!counter.isInitialized()) {
                              logger.info("uninitialized: " + name);
                              // skip uninitialized bc they won't have
                              // default values assigned in otel
                              return false;
                            }
                            if (!counter.getCounterpart().isEmitted()) {
                              logger.info("skipped: " + name);
                              return false;
                            }
                            logger.info(name + ": " + counter.getCounter());
                            return true;
                          })
                      .map(
                          counter ->
                              (Consumer<LongPointAssert>)
                                  points -> points.hasValue(counter.getCounter().apply()))
                      .collect(Collectors.toList());

              if (collect.isEmpty()) {
                logger.info("unset; skipping all assertions: " + name);
                return;
              }

              testing.waitAndAssertMetrics(
                  "twitter-util-stats",
                  name,
                  metrics ->
                      metrics.anySatisfy(
                          metric ->
                              assertThat(metric)
                                  .hasLongSumSatisfying(sum -> sum.hasPointsSatisfying(collect))));
            });

    TestStatsReceiver.getInstance()
        .getGauges()
        .asMap()
        .forEach(
            (name, gauges) -> {
              List<Consumer<DoublePointAssert>> collect =
                  gauges.stream()
                      .filter(
                          gauge -> {
                            if (!gauge.isInitialized()) {
                              logger.info("uninitialized: " + name);
                              // skip uninitialized bc they won't have
                              // default values assigned in otel
                              return false;
                            }
                            if (!gauge.getCounterpart().isEmitted()) {
                              logger.info("skipped: " + name);
                              return false;
                            }
                            logger.info(name + ": " + gauge.getLast());
                            return true;
                          })
                      .map(
                          gauge ->
                              (Consumer<DoublePointAssert>)
                                  points -> points.hasValue(gauge.getLast()))
                      .collect(Collectors.toList());

              if (collect.isEmpty()) {
                logger.info("unset; skipping all assertions: " + name);
                return;
              }

              testing.waitAndAssertMetrics(
                  "twitter-util-stats",
                  name,
                  metrics ->
                      metrics.anySatisfy(
                          metric ->
                              assertThat(metric)
                                  .hasDoubleGaugeSatisfying(
                                      sum -> sum.hasPointsSatisfying(collect))));
            });
  }
}
