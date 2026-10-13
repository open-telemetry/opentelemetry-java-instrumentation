/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApacheCamelInstrumentationModule extends InstrumentationModule {
  public ApacheCamelInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "camel-2.20" : "camel",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"camel"}
            : new String[] {"camel-2.20"});
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.contrib.awsxray.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CamelContextInstrumentation(), new SendProcessorInstrumentation());
  }
}
