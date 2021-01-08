/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.AwsXRayPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.extension.trace.propagation.OtTracerPropagator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
    initializePropagators(propagatorIds, () -> GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator(),
        p -> GlobalOpenTelemetry.get().setPropagators(p));
  }

  //exists for testing
  static void initializePropagators(List<String> propagatorIds, Supplier<TextMapPropagator> preconfiguredPropagator,
      Consumer<ContextPropagators> globalSetter) {
    ContextPropagators propagators = createPropagators(propagatorIds, preconfiguredPropagator.get());
    // Register it in the global propagators:
    globalSetter.accept(propagators);
  }

  private static ContextPropagators createPropagators(List<String> propagatorIds, TextMapPropagator preconfiguredPropagator) {
    /* Only override the default propagators *if* the caller specified any. */
    if (propagatorIds.size() == 0) {
      // TODO this is probably temporary until default propagators are supplied by SDK
      //  https://github.com/open-telemetry/opentelemetry-java/issues/1742
      return createPropagatorsRemovingNoops(Arrays.asList(preconfiguredPropagator,
          W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()));
    }

    List<Propagator> propagators = new ArrayList<>(propagatorIds.size());
    List<TextMapPropagator> textMapPropagators = new ArrayList<>();
    textMapPropagators.add(preconfiguredPropagator);

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
      textMapPropagators.addAll(propagators);
    } else if (propagators.size() == 1) {
      textMapPropagators.add(propagators.get(0));
    }
    return createPropagatorsRemovingNoops(textMapPropagators);
  }

  private static ContextPropagators createPropagatorsRemovingNoops(List<TextMapPropagator> textMapPropagators) {
    return ContextPropagators.create(TextMapPropagator.composite(
            textMapPropagators.stream()
                .filter(propagator -> propagator != TextMapPropagator.noop())
                .collect(toSet())
        ));
  }

  enum Propagator implements TextMapPropagator {
    // propagators that should always run:
    BAGGAGE("baggage", W3CBaggagePropagator.getInstance(), true),
    JAEGER("jaeger", JaegerPropagator.getInstance(), true),

    // propagators that can be skipped if context was already extracted:
    B3("b3", B3Propagator.getInstance(), false),
    B3_MULTI("b3multi", B3Propagator.builder().injectMultipleHeaders().build(), false),
    OT_TRACER("ottracer", OtTracerPropagator.getInstance(), false),
    TRACE_CONTEXT("tracecontext", W3CTraceContextPropagator.getInstance(), false),
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
