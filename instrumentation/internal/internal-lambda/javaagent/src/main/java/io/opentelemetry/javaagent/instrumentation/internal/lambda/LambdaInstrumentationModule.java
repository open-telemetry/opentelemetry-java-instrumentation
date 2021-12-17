/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.lambda;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.utility.JavaModule;

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
    // this instrumentation uses ASM not ByteBuddy so muzzle doesn't automatically add helper
    // classes
    List<String> classNames = new ArrayList<>();
    classNames.add("io.opentelemetry.javaagent.instrumentation.internal.lambda.LambdaTransformer");
    if (JavaModule.isSupported()) {
      classNames.add(
          "io.opentelemetry.javaagent.instrumentation.internal.lambda.Java9LambdaTransformer");
    }
    return classNames;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new InnerClassLambdaMetafactoryInstrumentation());
  }

  // adding this method here turns off the muzzle reference generation process
  // we don't want to have references to the java 9 transformer class because it is impossible to
  // load it on java 8 vms
  @SuppressWarnings({"unused", "rawtypes"})
  public Map getMuzzleReferences() {
    return Collections.emptyMap();
  }
}
