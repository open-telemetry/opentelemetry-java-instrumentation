/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ContextTestInstrumentationModule extends InstrumentationModule {
  public ContextTestInstrumentationModule() {
    super("context-test-instrumentation");
  }

  @Override
  protected boolean defaultEnabled() {
    // this instrumentation is disabled by default, so that it doesn't cause sporadic failures
    // in other tests that do override AgentTestRunner.onInstrumentationError() to filter out
    // the instrumentation errors that this instrumentation purposefully introduces
    return false;
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.equals(getClass().getPackage().getName() + ".Context");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ContextTestInstrumentation());
  }
}
