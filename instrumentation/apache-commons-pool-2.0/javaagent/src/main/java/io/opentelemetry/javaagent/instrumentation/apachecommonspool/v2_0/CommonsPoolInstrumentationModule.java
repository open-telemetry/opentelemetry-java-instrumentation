/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecommonspool.v2_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class CommonsPoolInstrumentationModule extends InstrumentationModule {
  public CommonsPoolInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "apache-commons-pool-2.0" : "apache-commons-pool",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"apache-commons-pool"}
            : new String[] {"apache-commons-pool-2.0"});
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new GenericObjectPoolInstrumentation());
  }
}
