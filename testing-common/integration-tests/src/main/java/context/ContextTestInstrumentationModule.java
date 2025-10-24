/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ContextTestInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public ContextTestInstrumentationModule() {
    super("context-test-instrumentation");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.equals(getClass().getPackage().getName() + ".Context");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ContextTestInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
