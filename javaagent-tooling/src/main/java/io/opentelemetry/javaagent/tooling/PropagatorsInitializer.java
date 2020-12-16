/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.propagation.HttpTraceContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.AwsXRayPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.extension.trace.propagation.OtTracerPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropagatorsInitializer {

  private static final Logger log = LoggerFactory.getLogger(PropagatorsInitializer.class);

  private static final Map<String, Propagator> TEXTMAP_PROPAGATORS;

  static {
    ImmutableMap.Builder<String, Propagator> propagators = ImmutableMap.builder();
    for (Propagator propagator : Propagator.values()) {
      propagators.put(propagator.id(), propagator);
    }
    TEXTMAP_PROPAGATORS = propagators.build();
  }

  /**
   * Initialize OpenTelemetry global Propagators with propagator list, if any.
   *
   * <p>Because TraceMultiPropagator returns first successful extracted Context and stops further
   * extraction, these rules are applied:
   *
   * <ul>
   *   <li>W3CBaggagePropagator and JaegerPropagator are added outside of the multi-propagator so
   *       that they will always runs and extract baggage (note: JaegerPropagator extracts both
   *       baggage and context).
   *   <li>W3CBaggagePropagator comes after JaegerPropagator, as it can have more complex/complete
   *       values that Jaeger baggage lacks, e.g. metadata. Baggage extraction can enrich the
   *       previous one.
   * </ul>
   */
  public static void initializePropagators(List<String> propagatorIds) {
    /* Only override the default propagators *if* the user specified any. */
    if (propagatorIds.size() == 0) {
      // TODO this is probably temporary until default propagators are supplied by SDK
      //  https://github.com/open-telemetry/opentelemetry-java/issues/1742
      setGlobalPropagators(
          DefaultContextPropagators.builder()
              .addTextMapPropagator(HttpTraceContext.getInstance())
              .addTextMapPropagator(W3CBaggagePropagator.getInstance())
              .build());
      return;
    }

    DefaultContextPropagators.Builder propagatorsBuilder = DefaultContextPropagators.builder();

    List<Propagator> propagators = new ArrayList<>(propagatorIds.size());

    for (String propagatorId : propagatorIds) {
      Propagator propagator = TEXTMAP_PROPAGATORS.get(propagatorId);
      if (propagator != null) {
        propagators.add(propagator);
        log.info("Added " + propagatorId + " propagator");
      } else {
        log.warn("No matching propagator for " + propagatorId);
      }
    }
    if (propagators.size() > 1) {
      propagatorsBuilder.addTextMapPropagator(new MultiPropagator(propagators));
    } else if (propagators.size() == 1) {
      propagatorsBuilder.addTextMapPropagator(propagators.get(0));
    }
    // Register it in the global propagators:
    setGlobalPropagators(propagatorsBuilder.build());
  }

  // Workaround https://github.com/open-telemetry/opentelemetry-java/pull/2096
  public static void setGlobalPropagators(ContextPropagators propagators) {
    OpenTelemetry.set(
        OpenTelemetrySdk.builder()
            .setResource(OpenTelemetrySdk.get().getResource())
            .setClock(OpenTelemetrySdk.get().getClock())
            .setMeterProvider(OpenTelemetry.getGlobalMeterProvider())
            .setTracerProvider(unobfuscate(OpenTelemetry.getGlobalTracerProvider()))
            .setPropagators(propagators)
            .build());
  }

  private static TracerProvider unobfuscate(TracerProvider tracerProvider) {
    if (tracerProvider.getClass().getName().endsWith("TracerSdkProvider")) {
      return tracerProvider;
    }
    try {
      Method unobfuscate = tracerProvider.getClass().getDeclaredMethod("unobfuscate");
      unobfuscate.setAccessible(true);
      return (TracerProvider) unobfuscate.invoke(tracerProvider);
    } catch (Throwable t) {
      return tracerProvider;
    }
  }

  static class MultiPropagator implements TextMapPropagator {
    private final List<Propagator> propagators;
    private final List<String> fields;

    private MultiPropagator(List<Propagator> propagators) {
      this.propagators = propagators;

      Set<String> fields = new LinkedHashSet<>();
      for (Propagator propagator : propagators) {
        fields.addAll(propagator.delegate.fields());
      }
      this.fields = new ArrayList<>(fields);
    }

    @Override
    public List<String> fields() {
      return fields;
    }

    @Override
    public <C> void inject(Context context, C carrier, Setter<C> setter) {
      for (Propagator propagator : propagators) {
        propagator.inject(context, carrier, setter);
      }
    }

    @Override
    public <C> Context extract(Context context, C carrier, Getter<C> getter) {
      boolean spanContextExtracted = false;
      for (int i = propagators.size() - 1; i >= 0; i--) {
        Propagator propagator = propagators.get(i);
        if (!propagator.alwaysRun() && spanContextExtracted) {
          continue;
        }
        context = propagator.extract(context, carrier, getter);
        if (!spanContextExtracted) {
          spanContextExtracted = isSpanContextExtracted(context);
        }
      }
      return context;
    }

    private static boolean isSpanContextExtracted(Context context) {
      return Span.fromContextOrNull(context) != null;
    }
  }

  enum Propagator implements TextMapPropagator {
    // propagators that should always run:
    BAGGAGE("baggage", W3CBaggagePropagator.getInstance(), true),
    JAEGER("jaeger", JaegerPropagator.getInstance(), true),

    // propagators that can be skipped if context was already extracted:
    B3("b3", B3Propagator.getInstance(), false),
    B3_MULTI("b3multi", B3Propagator.builder().injectMultipleHeaders().build(), false),
    OT_TRACER("ottracer", OtTracerPropagator.getInstance(), false),
    TRACE_CONTEXT("tracecontext", HttpTraceContext.getInstance(), false),
    XRAY("xray", AwsXRayPropagator.getInstance(), false);

    private final String id;
    private final TextMapPropagator delegate;
    private final boolean alwaysRun;

    Propagator(String id, TextMapPropagator delegate, boolean alwaysRun) {
      this.id = id;
      this.delegate = delegate;
      this.alwaysRun = alwaysRun;
    }

    String id() {
      return id;
    }

    boolean alwaysRun() {
      return alwaysRun;
    }

    @Override
    public Collection<String> fields() {
      return delegate.fields();
    }

    @Override
    public <C> void inject(Context context, C carrier, Setter<C> setter) {
      delegate.inject(context, carrier, setter);
    }

    @Override
    public <C> Context extract(Context context, C carrier, Getter<C> getter) {
      return delegate.extract(context, carrier, getter);
    }
  }
}
