/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LibraryTestRunnerTest {
  static {
    // TODO: remove once test dependency on javaagent-tooling is removed
    System.setProperty("io.opentelemetry.context.contextStorageProvider", "default");
  }

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void baggagePropagationAndSpanAttributes() {
    Map<String, String> carrier = new HashMap<>();
    carrier.put(
        "baggage",
        "test-baggage-key-1=test-baggage-value-1,test-baggage-key-2=test-baggage-value-2,other=value");
    Context extracted =
        testing
            .getOpenTelemetry()
            .getPropagators()
            .getTextMapPropagator()
            .extract(Context.root(), carrier, new MapGetter());

    try (Scope ignored = extracted.makeCurrent()) {
      assertThat(Baggage.current().getEntryValue("test-baggage-key-1"))
          .isEqualTo("test-baggage-value-1");
      assertThat(Baggage.current().getEntryValue("test-baggage-key-2"))
          .isEqualTo("test-baggage-value-2");
      Map<String, String> injected = new HashMap<>();
      testing
          .getOpenTelemetry()
          .getPropagators()
          .getTextMapPropagator()
          .inject(Context.current(), injected, Map::put);
      assertThat(injected.get("baggage"))
          .contains(
              "test-baggage-key-1=test-baggage-value-1",
              "test-baggage-key-2=test-baggage-value-2",
              "other=value");
      testing.runWithSpan("test", () -> {});
    }

    boolean copyBaggage =
        !System.getProperty("otel.java.experimental.span-attributes.copy-from-baggage.include", "")
            .isEmpty();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("test-baggage-key-1"),
                                copyBaggage ? "test-baggage-value-1" : null),
                            equalTo(
                                stringKey("test-baggage-key-2"),
                                copyBaggage ? "test-baggage-value-2" : null))));
  }

  private static class MapGetter implements TextMapGetter<Map<String, String>> {
    @Override
    public Iterable<String> keys(Map<String, String> carrier) {
      return carrier.keySet();
    }

    @Override
    public String get(Map<String, String> carrier, String key) {
      return carrier.get(key);
    }
  }
}
