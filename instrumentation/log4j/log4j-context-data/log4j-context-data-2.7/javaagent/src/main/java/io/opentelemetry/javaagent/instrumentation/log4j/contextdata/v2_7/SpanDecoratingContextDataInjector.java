/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.contextdata.v2_7;

import static io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants.SPAN_ID;
import static io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants.TRACE_FLAGS;
import static io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants.TRACE_ID;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.internal.ConfiguredResourceAttributesHolder;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.core.ContextDataInjector;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;

public final class SpanDecoratingContextDataInjector implements ContextDataInjector {
  private static final boolean BAGGAGE_ENABLED =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.log4j-context-data.add-baggage", false);

  private static final StringMap staticContextData = getStaticContextData();

  private final ContextDataInjector delegate;

  public SpanDecoratingContextDataInjector(ContextDataInjector delegate) {
    this.delegate = delegate;
  }

  @Override
  public StringMap injectContextData(List<Property> list, StringMap stringMap) {
    StringMap contextData = delegate.injectContextData(list, stringMap);

    if (contextData.containsKey(TRACE_ID)) {
      // Assume already instrumented event if traceId is present.
      return staticContextData.isEmpty() ? contextData : newContextData(contextData);
    }

    Context context = Context.current();
    Span span = Span.fromContext(context);
    SpanContext currentContext = span.getSpanContext();
    if (!currentContext.isValid()) {
      return staticContextData.isEmpty() ? contextData : newContextData(contextData);
    }

    StringMap newContextData = newContextData(contextData);
    newContextData.putValue(TRACE_ID, currentContext.getTraceId());
    newContextData.putValue(SPAN_ID, currentContext.getSpanId());
    newContextData.putValue(TRACE_FLAGS, currentContext.getTraceFlags().asHex());

    if (BAGGAGE_ENABLED) {
      Baggage baggage = Baggage.fromContext(context);
      for (Map.Entry<String, BaggageEntry> entry : baggage.asMap().entrySet()) {
        // prefix all baggage values to avoid clashes with existing context
        newContextData.putValue("baggage." + entry.getKey(), entry.getValue().getValue());
      }
    }
    return newContextData;
  }

  @Override
  public ReadOnlyStringMap rawContextData() {
    return delegate.rawContextData();
  }

  private static StringMap newContextData(StringMap contextData) {
    StringMap newContextData = new SortedArrayStringMap(contextData);
    newContextData.putAll(staticContextData);
    return newContextData;
  }

  private static StringMap getStaticContextData() {
    StringMap map = new SortedArrayStringMap();
    for (Map.Entry<String, String> entry :
        ConfiguredResourceAttributesHolder.getResourceAttributes().entrySet()) {
      map.putValue(entry.getKey(), entry.getValue());
    }
    return map;
  }
}
