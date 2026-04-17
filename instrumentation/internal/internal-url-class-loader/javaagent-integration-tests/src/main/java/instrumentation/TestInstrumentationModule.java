/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class TestInstrumentationModule extends InstrumentationModule {
  public TestInstrumentationModule() {
    super("test-instrumentation");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new TestTypeInstrumentation());
  }
}
