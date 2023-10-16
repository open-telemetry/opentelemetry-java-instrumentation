/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata.v2_17;

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_FLAGS;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.javaagent.bootstrap.ConfiguredResourceAttributesHolder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.core.util.ContextDataProvider;

/**
 * Implementation of Log4j 2's {@link ContextDataProvider} which is loaded via SPI. {@link
 * #supplyContextData()} is called when a log entry is created.
 */
public class OpenTelemetryContextDataProvider implements ContextDataProvider {

  private boolean configuredResourceAttributeAccessible;

  public OpenTelemetryContextDataProvider() {
    try {
      Class.forName("io.opentelemetry.javaagent.bootstrap.ConfiguredResourceAttributesHolder");
      this.configuredResourceAttributeAccessible = true;

    } catch (ClassNotFoundException ok) {
      this.configuredResourceAttributeAccessible = false;
    }
  }

  private static final boolean BAGGAGE_ENABLED =
      ConfigPropertiesUtil.getBoolean("otel.instrumentation.log4j-context-data.add-baggage", false);

  /**
   * Returns context from the current span when available.
   *
   * @return A map containing string versions of the traceId, spanId, and traceFlags, which can then
   *     be accessed from layout components
   */
  @Override
  public Map<String, String> supplyContextData() {
    Context context = Context.current();
    Span currentSpan = Span.fromContext(context);
    if (!currentSpan.getSpanContext().isValid()) {
      return Collections.emptyMap();
    }

    Map<String, String> contextData = new HashMap<>();
    SpanContext spanContext = currentSpan.getSpanContext();
    contextData.put(TRACE_ID, spanContext.getTraceId());
    contextData.put(SPAN_ID, spanContext.getSpanId());
    contextData.put(TRACE_FLAGS, spanContext.getTraceFlags().asHex());

    if (configuredResourceAttributeAccessible) {
      contextData.putAll(ConfiguredResourceAttributesHolder.getResourceAttributes());
    }

    if (BAGGAGE_ENABLED) {
      Baggage baggage = Baggage.fromContext(context);
      for (Map.Entry<String, BaggageEntry> entry : baggage.asMap().entrySet()) {
        // prefix all baggage values to avoid clashes with existing context
        contextData.put("baggage." + entry.getKey(), entry.getValue().getValue());
      }
    }

    return contextData;
  }
}
