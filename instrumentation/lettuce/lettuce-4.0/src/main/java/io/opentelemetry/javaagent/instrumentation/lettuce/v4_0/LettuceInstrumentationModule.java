/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class LettuceInstrumentationModule extends InstrumentationModule {
  public LettuceInstrumentationModule() {
    super("lettuce", "lettuce-4.0");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".LettuceAbstractDatabaseClientTracer",
      packageName + ".LettuceConnectionDatabaseClientTracer",
      packageName + ".LettuceDatabaseClientTracer",
      packageName + ".InstrumentationPoints"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new LettuceClientInstrumentation(), new LettuceAsyncCommandsInstrumentation());
  }
}
