/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.ratpack.server.AbstractRatpackHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import org.junit.jupiter.api.extension.RegisterExtension;
import ratpack.server.RatpackServerSpec;

class RatpackHttpServerTest extends AbstractRatpackHttpServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(RatpackServerSpec serverSpec) {}

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setHasResponseCustomizer(endpoint -> true);
  }

  @Override
  protected void assertExceptionLogs(Throwable expectedException, String expectedEventName) {
    super.assertExceptionLogs(expectedException, expectedEventName);
    assertThat(testing.spans())
        .filteredOn(span -> span.getKind() == SpanKind.SERVER)
        .singleElement()
        .satisfies(
            serverSpan ->
                assertThat(testing.logRecords())
                    .filteredOn(log -> expectedEventName.equals(log.getEventName()))
                    .singleElement()
                    .satisfies(
                        log -> {
                          assertThat(log.getSpanContext()).isEqualTo(serverSpan.getSpanContext());
                          assertThat(log.getInstrumentationScopeInfo())
                              .isEqualTo(serverSpan.getInstrumentationScopeInfo());
                          assertThat(log.getInstrumentationScopeInfo().getName())
                              .isEqualTo("io.opentelemetry.netty-4.1");
                        }));
  }
}
