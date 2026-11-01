/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContextPropagationTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void extractRepeatedBaggageHeaders(boolean nullCarrier) {
    Map<String, List<String>> headers =
        singletonMap("baggage", asList("first=one", "second=two", "third=three"));
    Map<String, List<String>> carrier = nullCarrier ? null : headers;
    TextMapGetter<Map<String, List<String>>> getter =
        new TextMapGetter<Map<String, List<String>>>() {
          @Override
          public String get(Map<String, List<String>> actualCarrier, String key) {
            assertThat(actualCarrier).isSameAs(carrier);
            List<String> values = headers.get(key);
            return values == null ? null : values.get(0);
          }

          @Override
          public Iterator<String> getAll(Map<String, List<String>> actualCarrier, String key) {
            assertThat(actualCarrier).isSameAs(carrier);
            return headers.getOrDefault(key, emptyList()).iterator();
          }
        };

    Context extracted =
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Context.root(), carrier, getter);

    assertThat(Baggage.fromContext(extracted).size()).isEqualTo(3);
    assertThat(Baggage.fromContext(extracted).getEntryValue("first")).isEqualTo("one");
    assertThat(Baggage.fromContext(extracted).getEntryValue("second")).isEqualTo("two");
    assertThat(Baggage.fromContext(extracted).getEntryValue("third")).isEqualTo("three");
  }

  @Test
  void extractBaggageWithDefaultGetAll() {
    TextMapGetter<Object> getter =
        (carrier, key) -> "baggage".equals(key) ? "first=one,second=two" : null;

    Context extracted =
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Context.root(), null, getter);

    assertThat(Baggage.fromContext(extracted).size()).isEqualTo(2);
    assertThat(Baggage.fromContext(extracted).getEntryValue("first")).isEqualTo("one");
    assertThat(Baggage.fromContext(extracted).getEntryValue("second")).isEqualTo("two");
  }

  @Test
  void emptyHeadersPreserveContext() {
    Context context =
        Baggage.builder().put("existing", "value").build().storeInContext(Context.root());
    TextMapGetter<Object> getter =
        new TextMapGetter<Object>() {
          @Override
          public String get(Object carrier, String key) {
            return null;
          }

          @Override
          public Iterator<String> getAll(Object carrier, String key) {
            return emptyIterator();
          }
        };

    assertThat(
            GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(context, null, getter))
        .isSameAs(context);
  }
}
