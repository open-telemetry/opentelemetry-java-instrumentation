/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context.jpms;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

public class ExposeRmiModuleInstrumentation implements TypeInstrumentation {
  private static final Logger logger =
      Logger.getLogger(ExposeRmiModuleInstrumentation.class.getName());

  private final AtomicBoolean instrumented = new AtomicBoolean();

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    ElementMatcher.Junction<TypeDescription> notInstrumented =
        new ElementMatcher.Junction.AbstractBase<TypeDescription>() {

          @Override
          public boolean matches(TypeDescription target) {
            return !instrumented.get();
          }
        };

    return notInstrumented.and(nameStartsWith("sun.rmi"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return new ElementMatcher.Junction.AbstractBase<ClassLoader>() {

      @Override
      public boolean matches(ClassLoader target) {
        // runs only in bootstrap class loader
        return JavaModule.isSupported() && target == null;
      }
    };
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, module) -> {
          if (module != null && module.isNamed()) {
            // using InstrumentationVersion because it's in the unnamed module in the bootstrap
            // loader, and that's where the rmi instrumentation helper classes will end up
            JavaModule helperModule = JavaModule.ofType(InstrumentationVersion.class);
            // expose sun.rmi.server package to unnamed module
            ClassInjector.UsingInstrumentation.redefineModule(
                InstrumentationHolder.getInstrumentation(),
                module,
                Collections.emptySet(),
                Collections.singletonMap("sun.rmi.server", Collections.singleton(helperModule)),
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptyMap());

            instrumented.set(true);
            logger.fine(
                "Exposed package \"sun.rmi.server\" in module " + module + " to unnamed module");
          }
          return builder;
        });
  }
}
