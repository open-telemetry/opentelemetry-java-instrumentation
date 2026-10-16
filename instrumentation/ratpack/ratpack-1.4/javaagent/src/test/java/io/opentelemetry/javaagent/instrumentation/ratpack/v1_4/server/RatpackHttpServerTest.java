/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.server;

import static org.assertj.core.api.Assertions.assertThat;

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
  protected void assertExceptionLogs(Throwable expectedException, String expectedEventName) {
    super.assertExceptionLogs(expectedException, expectedEventName);
    testing.logRecords().stream()
        .filter(log -> expectedEventName.equals(log.getEventName()))
        .forEach(
            log ->
                assertThat(log.getInstrumentationScopeInfo().getName())
                    .isEqualTo("io.opentelemetry.netty-4.1"));
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setHasResponseCustomizer(endpoint -> true);
  }
}
