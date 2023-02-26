/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class R2dbcInstrumentationModule extends InstrumentationModule {

  public R2dbcInstrumentationModule() {
    super("r2dbc", "r2dbc-1.0");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.r2dbc.proxy");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new R2dbcInstrumentation());
  }
}
