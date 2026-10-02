/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive.v1_0.stage;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class HibernateReactiveStageInstrumentationModule extends InstrumentationModule {

  public HibernateReactiveStageInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "hibernate-reactive-1.0" : "hibernate-reactive",
        // In v3 preview, the hibernate selector is reserved for default-off synchronous modules.
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"hibernate-reactive"}
            : new String[] {"hibernate-reactive-1.0", "hibernate-reactive-stage"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new StageSessionFactoryInstrumentation(), new StageSessionImplInstrumentation());
  }
}
