/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package helper;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class DuplicateHelperInstrumentationModule extends InstrumentationModule {
  public DuplicateHelperInstrumentationModule() {
    super("duplicate-helper");
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    // muzzle adds the same class as helper, listing it twice to ensure it doesn't fail
    return Arrays.asList("helper.DuplicateHelper", "helper.DuplicateHelper");
  }

  @Override
  public boolean isHelperClass(String className) {
    return "helper.DuplicateHelper".equals(className);
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new DuplicateHelperInstrumentation());
  }
}
