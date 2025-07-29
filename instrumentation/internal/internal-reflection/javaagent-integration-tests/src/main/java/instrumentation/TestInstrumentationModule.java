/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class TestInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public TestInstrumentationModule() {
    super("test-instrumentation");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new TestTypeInstrumentation());
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    // makes the class from instrumentation from the instrumented class with inlined advice
    return Arrays.asList("instrumentation.TestHelperClass");
  }

  @Override
  public void injectClasses(ClassInjector injector) {
    injector
        .proxyBuilder("instrumentation.TestHelperClass")
        .inject(InjectionMode.CLASS_AND_RESOURCE);
  }
}
