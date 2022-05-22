/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HttpRouteHolderTest {

  @RegisterExtension final OpenTelemetryExtension testing = OpenTelemetryExtension.create();

  @Test
  void shouldSetRouteEvenIfSpanIsNotSampled() {
    Instrumenter<String, Void> instrumenter =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "test", s -> s)
            .addContextCustomizer(HttpRouteHolder.get())
            .newInstrumenter();

    Context context = instrumenter.start(Context.root(), "test");

    assertNull(HttpRouteHolder.getRoute(context));

    HttpRouteHolder.updateHttpRoute(context, HttpRouteSource.SERVLET, "/get/:id");

    assertEquals("/get/:id", HttpRouteHolder.getRoute(context));
  }

  // TODO(mateusz): add more unit tests for HttpRouteHolder
}
