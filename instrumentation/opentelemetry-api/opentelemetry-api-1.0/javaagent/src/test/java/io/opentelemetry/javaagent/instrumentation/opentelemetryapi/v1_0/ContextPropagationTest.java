/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContextPropagationTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void extractBaggageWithLegacyGetter(boolean nullCarrier) {
    Map<String, String> headers = singletonMap("baggage", "first=one,second=two");
    Map<String, String> carrier = nullCarrier ? null : headers;
    TextMapGetter<Map<String, String>> getter =
        new TextMapGetter<Map<String, String>>() {
          @Override
          public Iterable<String> keys(Map<String, String> actualCarrier) {
            return singletonList("baggage");
          }

          @Override
          public String get(Map<String, String> actualCarrier, String key) {
            assertThat(actualCarrier).isSameAs(carrier);
            return headers.get(key);
          }
        };

    Context extracted =
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Context.root(), carrier, getter);

    assertThat(Baggage.fromContext(extracted).size()).isEqualTo(2);
    assertThat(Baggage.fromContext(extracted).getEntryValue("first")).isEqualTo("one");
    assertThat(Baggage.fromContext(extracted).getEntryValue("second")).isEqualTo("two");
  }

  @Test
  void missingHeadersPreserveContext() {
    Context context =
        Baggage.builder().put("existing", "value").build().storeInContext(Context.root());
    TextMapGetter<Object> getter =
        new TextMapGetter<Object>() {
          @Override
          public Iterable<String> keys(Object carrier) {
            return singletonList("baggage");
          }

          @Override
          public String get(Object carrier, String key) {
            return null;
          }
        };

    assertThat(
            GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(context, null, getter))
        .isSameAs(context);
  }
}
