/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ElasticJobInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public ElasticJobInstrumentationModule() {
    super("apache-elasticjob", "apache-elasticjob-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new ElasticJobExecutorInstrumentation(),
        new SimpleJobExecutorInstrumentation(),
        new DataflowJobExecutorInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
