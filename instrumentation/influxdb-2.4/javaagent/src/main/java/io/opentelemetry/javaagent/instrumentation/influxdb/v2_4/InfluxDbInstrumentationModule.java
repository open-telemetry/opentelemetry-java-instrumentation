/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class InfluxDbInstrumentationModule extends InstrumentationModule {

  public InfluxDbInstrumentationModule() {
    super("influxdb-2.4", "influxdb");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new InfluxDbImplInstrumentation(),
        new InfluxDbCallbackInstrumentation(),
        new InfluxDbExecutorCallbackInstrumentation());
  }
}
