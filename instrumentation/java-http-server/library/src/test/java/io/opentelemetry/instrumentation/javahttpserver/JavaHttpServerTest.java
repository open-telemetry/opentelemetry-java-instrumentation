/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpserver;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;

class JavaHttpServerTest extends AbstractJavaHttpServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected void configureContexts(List<HttpContext> contexts) {
    Filter filter =
        JavaHttpServerTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(
                Collections.singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
            .setCapturedResponseHeaders(
                Collections.singletonList(AbstractHttpServerTest.TEST_RESPONSE_HEADER))
            .build()
            .newFilter();
    contexts.forEach(ctx -> ctx.getFilters().add(filter));
  }
}
