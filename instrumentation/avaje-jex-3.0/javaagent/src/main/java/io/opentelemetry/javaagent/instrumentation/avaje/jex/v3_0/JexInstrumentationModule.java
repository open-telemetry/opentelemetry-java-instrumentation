/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.avaje.jex.v3_0;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@SuppressWarnings("unused")
@AutoService(InstrumentationModule.class)
public class JexInstrumentationModule extends InstrumentationModule {

  public JexInstrumentationModule() {
    super("avaje-jex", "avaje-jex-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JexInstrumentation());
  }
}
