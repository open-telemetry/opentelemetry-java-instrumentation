/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.osgi;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.test.junit5.context.BundleContextExtension;

@ExtendWith(BundleContextExtension.class)
class ApiOsgiTest {

  @Test
  void contextStorageWorksInOsgi() {
    ContextKey<String> key = ContextKey.named("test");
    try (Scope ignored = Context.current().with(key, "value").makeCurrent()) {
      assertEquals("value", Context.current().get(key));
    }
  }

  @Test
  void instrumenterBuilds() {
    SpanNameExtractor<String> nameExtractor = request -> request;
    Instrumenter<String, String> instrumenter =
        Instrumenter.<String, String>builder(OpenTelemetry.noop(), "test", nameExtractor)
            .buildServerInstrumenter(NoopGetter.INSTANCE);
    assertNotNull(instrumenter);
  }

  @Test
  void withSpanAnnotationIsAvailable() {
    assertEquals("io.opentelemetry.instrumentation.annotations.WithSpan", WithSpan.class.getName());
  }

  private enum NoopGetter implements TextMapGetter<String> {
    INSTANCE;

    @Override
    public Iterable<String> keys(String carrier) {
      return emptyList();
    }

    @Override
    public String get(String carrier, String key) {
      return null;
    }
  }
}
