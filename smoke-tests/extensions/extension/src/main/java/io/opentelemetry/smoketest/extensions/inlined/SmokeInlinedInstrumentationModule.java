/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.extensions.inlined;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SmokeInlinedInstrumentationModule extends InstrumentationModule {

  public SmokeInlinedInstrumentationModule() {
    super("smoke-test-extension-inlined");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new SmokeInlinedInstrumentation());
  }
}
