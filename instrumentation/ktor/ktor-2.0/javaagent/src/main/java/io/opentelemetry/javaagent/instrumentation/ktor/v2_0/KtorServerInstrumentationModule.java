/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ktor.v2_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KtorServerInstrumentationModule extends InstrumentationModule {

  public KtorServerInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "ktor-2.0" : "ktor",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"ktor-server", "ktor"}
            : new String[] {"ktor-2.0", "ktor-server", "ktor-server-2.0"});
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.extension.kotlin.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ServerInstrumentation());
  }
}
