/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors.metrics;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.EarlyInstrumentationModule;
import java.util.List;

@AutoService({InstrumentationModule.class, EarlyInstrumentationModule.class})
public class ExecutorsMetricsInstrumentationModule extends InstrumentationModule
    implements EarlyInstrumentationModule {

  public ExecutorsMetricsInstrumentationModule() {
    super("executors-metrics", "executors");
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ThreadPoolExecutorMetricsInstrumentation(),
        new ThreadPerTaskExecutorMetricsInstrumentation());
  }
}
