/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheshenyu.v2_4;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ApacheShenYuInstrumentationModule extends InstrumentationModule {
  public ApacheShenYuInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "apache-shenyu-2.4" : "apache-shenyu",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"apache-shenyu"}
            : new String[] {"apache-shenyu-2.4"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ContextBuilderInstrumentation());
  }
}
