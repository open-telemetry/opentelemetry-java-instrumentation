/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.lambda;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class LambdaInstrumentationModule extends InstrumentationModule {
  public LambdaInstrumentationModule() {
    super("internal-lambda");
  }

  @Override
  public boolean defaultEnabled() {
    // internal instrumentations are always enabled by default
    return true;
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    // The ASM-inserted call is not visible to Muzzle's advice scanning.
    return singletonList(
        "io.opentelemetry.javaagent.instrumentation.internal.lambda.LambdaTransformerHelper");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new InnerClassLambdaMetafactoryInstrumentation());
  }
}
