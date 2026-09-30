/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher.v20_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class LibertyDispatcherInstrumentationModule extends InstrumentationModule {

  public LibertyDispatcherInstrumentationModule() {
    super(
        "liberty-dispatcher",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"liberty-dispatcher-20.0"}
            : new String[] {"liberty-dispatcher-20.0", "liberty"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new LibertyDispatcherLinkInstrumentation());
  }
}
