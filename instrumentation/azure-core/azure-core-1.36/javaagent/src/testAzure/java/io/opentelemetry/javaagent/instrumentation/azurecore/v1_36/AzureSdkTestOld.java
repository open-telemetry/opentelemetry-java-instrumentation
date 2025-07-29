/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurecore.v1_36;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.util.Context;
import com.azure.core.util.tracing.Tracer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// some azure sdks (e.g. EventHubs) are still looking up Tracer via service loader
// and not yet using the new TracerProvider
class AzureSdkTestOld {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testHelperClassesInjected() {
    com.azure.core.util.tracing.Tracer azTracer = createAzTracer();
    assertThat(azTracer.isEnabled()).isTrue();

    assertThat(azTracer.getClass().getName())
        .isEqualTo(
            "io.opentelemetry.javaagent.instrumentation.azurecore.v1_36.shaded"
                + ".com.azure.core.tracing.opentelemetry.OpenTelemetryTracer");
  }

  @Test
  void testSpan() {
    com.azure.core.util.tracing.Tracer azTracer = createAzTracer();
    Context context = azTracer.start("hello", Context.NONE);
    azTracer.end(null, null, context);

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("hello")
                        .hasKind(SpanKind.INTERNAL)
                        .hasStatus(StatusData.unset())
                        .hasAttributes(Attributes.empty())));
  }

  private static com.azure.core.util.tracing.Tracer createAzTracer() {
    Iterable<com.azure.core.util.tracing.Tracer> tracers =
        ServiceLoader.load(com.azure.core.util.tracing.Tracer.class);
    Iterator<Tracer> it = tracers.iterator();
    return it.hasNext() ? it.next() : null;
  }
}
