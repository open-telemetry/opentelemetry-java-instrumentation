/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.lambda;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.utility.JavaModule;

@AutoService(InstrumentationModule.class)
public class LambdaInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public LambdaInstrumentationModule() {
    super("internal-lambda");
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // internal instrumentations are always enabled by default
    return true;
  }

  @Override
  public List<String> injectedClassNames() {
    List<String> classNames = new ArrayList<>();
    classNames.add("io.opentelemetry.javaagent.instrumentation.internal.lambda.LambdaTransformer");
    if (JavaModule.isSupported()) {
      classNames.add(
          "io.opentelemetry.javaagent.instrumentation.internal.lambda.Java9LambdaTransformer");
    }
    return classNames;
  }

  @Override
  public void injectClasses(ClassInjector injector) {
    injector
        .proxyBuilder(
            "io.opentelemetry.javaagent.instrumentation.internal.lambda.LambdaTransformer")
        .inject(InjectionMode.CLASS_ONLY);
    if (JavaModule.isSupported()) {
      injector
          .proxyBuilder(
              "io.opentelemetry.javaagent.instrumentation.internal.lambda.Java9LambdaTransformer")
          .inject(InjectionMode.CLASS_ONLY);
    }
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    // this instrumentation uses ASM not ByteBuddy so muzzle doesn't automatically add helper
    // classes
    return injectedClassNames();
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new InnerClassLambdaMetafactoryInstrumentation());
  }
}
