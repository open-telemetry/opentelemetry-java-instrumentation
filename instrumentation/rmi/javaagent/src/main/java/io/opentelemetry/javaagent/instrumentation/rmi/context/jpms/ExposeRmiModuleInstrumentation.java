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
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExposeRmiModuleInstrumentation implements TypeInstrumentation {
  private static final Logger logger =
      LoggerFactory.getLogger(ExposeRmiModuleInstrumentation.class);
  private static final AtomicBoolean instrumented = new AtomicBoolean();

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
  public void transform(TypeTransformer transformer) {
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, module) -> {
          if (!instrumented.get() && module != null && module.isNamed()) {
            // unnamed module in bootstrap loader, instead of InstrumentationVersion could use
            // any class that is in agent bootstrap
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
            logger.debug(
                "Exposed package \"sun.rmi.server\" in module {} to unnamed module", module);
          }
          return builder;
        });
  }
}
