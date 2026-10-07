/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spark.v2_3;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SparkInstrumentationModule extends InstrumentationModule {

  public SparkInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "spark-2.3" : "spark",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"spark"}
            : new String[] {"spark-2.3"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RoutesInstrumentation());
  }
}
