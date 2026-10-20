/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.builder.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.semconv.SchemaUrls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpInstrumenterSchemaTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @Mock HttpClientAttributesGetter<String, String> clientGetter;
  @Mock HttpServerAttributesGetter<String, String> serverGetter;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void emitsHttpSchemaOnSignals(boolean client) {
    Instrumenter<String, String> instrumenter =
        client
            ? DefaultHttpClientInstrumenterBuilder.create(
                    "test", otelTesting.getOpenTelemetry(), clientGetter)
                .build()
            : DefaultHttpServerInstrumenterBuilder.create(
                    "test", otelTesting.getOpenTelemetry(), serverGetter)
                .build();

    Context context = instrumenter.start(Context.root(), "request");
    instrumenter.end(context, "request", null, new IllegalStateException("test"));

    assertThat(otelTesting.getSpans())
        .singleElement()
        .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_41_0));
    assertThat(otelTesting.getMetrics())
        .singleElement()
        .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_41_0));
    if (emitExceptionAsLogs()) {
      assertThat(otelTesting.getLogRecords())
          .singleElement()
          .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_41_0));
    } else {
      assertThat(otelTesting.getLogRecords()).isEmpty();
    }
  }

  @Test
  void customClientInstrumenterEmitsHttpSchema() {
    Instrumenter<String, String> instrumenter =
        DefaultHttpClientInstrumenterBuilder.create(
                "test", otelTesting.getOpenTelemetry(), clientGetter)
            .<String, String>instrumenterBuilder(request -> "request")
            .buildInstrumenter();

    Context context = instrumenter.start(Context.root(), "request");
    instrumenter.end(context, "request", "response", null);

    assertThat(otelTesting.getSpans())
        .singleElement()
        .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_41_0));
  }
}
