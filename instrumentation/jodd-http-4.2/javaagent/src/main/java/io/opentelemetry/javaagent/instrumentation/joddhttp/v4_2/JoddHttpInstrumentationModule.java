/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JoddHttpInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public JoddHttpInstrumentationModule() {
    super("jodd-http", "jodd-http-4.2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JoddHttpInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
