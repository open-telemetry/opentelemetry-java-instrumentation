/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardmetrics;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class DropwizardMetricsInstrumentationModule extends InstrumentationModule {

  public DropwizardMetricsInstrumentationModule() {
    super("dropwizard-metrics", "dropwizard-metrics-4.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // removed in 4.0
    return not(hasClassesNamed("com.codahale.metrics.LongAdder"));
  }

  @Override
  public boolean defaultEnabled() {
    // the Dropwizard metrics API does not have a concept of metric labels/tags/attributes, thus the
    // data produced by this integration might be of very low quality, depending on how the API is
    // used in the instrumented application
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new MetricRegistryInstrumentation(),
        new CounterInstrumentation(),
        new HistogramInstrumentation(),
        new MeterInstrumentation(),
        new TimerInstrumentation());
  }
}
