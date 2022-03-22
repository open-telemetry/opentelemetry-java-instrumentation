/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.shenyu;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ShenYuInstrumentationModule extends InstrumentationModule {

  public ShenYuInstrumentationModule() {
    super("shenyu");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ShenYuExecuteInstrumentation(), new ShenYuDoExecuteInstrumentation());
  }
}
