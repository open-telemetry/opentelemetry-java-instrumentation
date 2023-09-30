/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JoddHttpInstrumentationModule extends InstrumentationModule {

  public JoddHttpInstrumentationModule() {
    super("jodd-http", "jodd-http-4.2");
  }

  @Override
  public boolean isIndyModule() {
    // JoddHttpHttpAttributesGetterTest is not an agent test, with indy it can't access
    // JoddHttpHttpAttributesGetter
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new JoddHttpInstrumentation());
  }
}
