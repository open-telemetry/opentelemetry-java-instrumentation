/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbcmysql.v0_8;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class R2dbcInstrumentationModule extends InstrumentationModule {
  public R2dbcInstrumentationModule() {
    super("r2dbc");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new QueryFlowInstrumentation(),
        new ReactorNettyClientInstrumentation(),
        new TextQueryInstrumentation());
  }
}
