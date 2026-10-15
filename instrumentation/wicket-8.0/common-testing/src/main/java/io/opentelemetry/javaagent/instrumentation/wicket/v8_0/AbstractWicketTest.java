/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.wicket.v8_0;

import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractWicketTest<SERVER> extends AbstractHttpServerUsingTest<SERVER> {

  @RegisterExtension
  private static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  protected String getContextPath() {
    return "/jetty-context";
  }

  @BeforeAll
  void setup() {
    startServer();
  }

  @AfterAll
  void cleanUp() {
    cleanupServer();
  }

  @Test
  void testHello() {
    AggregatedHttpResponse response =
        client.get(h1Address.resolve("wicket-test/").toString()).aggregate().join();
    Document doc = Jsoup.parse(response.contentUtf8());

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(doc.selectFirst("#message").text()).isEqualTo("Hello World!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + getContextPath() + "/wicket-test/hello.HelloPage")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)));
  }

  @Test
  void testException() {
    AggregatedHttpResponse response =
        client.get(h1Address.resolve("wicket-test/exception").toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(500);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + getContextPath() + "/wicket-test/hello.ExceptionPage")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(
                            SemconvExceptionSignal.emitExceptionAsSpanEvents()
                                ? new Exception("test exception")
                                : null)
                        .hasEventsSatisfying(
                            events ->
                                assertThat(events)
                                    .filteredOn(event -> "exception".equals(event.getName()))
                                    .hasSize(
                                        SemconvExceptionSignal.emitExceptionAsSpanEvents()
                                            ? 1
                                            : 0))));

    List<LogRecordData> logs =
        testing.logRecords().stream()
            .filter(log -> "http.server.request.exception".equals(log.getEventName()))
            .collect(toList());
    assertThat(logs).hasSize(SemconvExceptionSignal.emitExceptionAsLogs() ? 1 : 0);
    if (SemconvExceptionSignal.emitExceptionAsLogs()) {
      LogRecordData log = logs.get(0);
      assertThat(log.getSeverity()).isEqualTo(Severity.ERROR);
      assertThat(log.getEventName()).isEqualTo("http.server.request.exception");
      assertThat(log.getAttributes().get(EXCEPTION_TYPE)).isEqualTo(Exception.class.getName());
      assertThat(log.getAttributes().get(EXCEPTION_MESSAGE)).isEqualTo("test exception");
      assertThat(log.getAttributes().get(EXCEPTION_STACKTRACE)).isNotNull();
      assertThat(testing.spans())
          .anySatisfy(span -> assertThat(span.getSpanContext()).isEqualTo(log.getSpanContext()));
    }
  }

  @Test
  void testResource() {
    AggregatedHttpResponse response =
        client.get(h1Address.resolve("wicket-test/resource").toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("hello resource");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + getContextPath() + "/wicket-test/hello.HelloResource")
                        .hasAttribute(
                            HTTP_ROUTE, getContextPath() + "/wicket-test/hello.HelloResource")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()));
  }
}
