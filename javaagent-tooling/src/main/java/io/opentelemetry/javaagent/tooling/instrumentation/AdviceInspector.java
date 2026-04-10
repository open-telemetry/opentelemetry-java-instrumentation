/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;

class AdviceInspector {

  private final ClassFileLocator classFileLocator;

  AdviceInspector(ClassFileLocator classFileLocator) {
    this.classFileLocator = classFileLocator;
  }

  boolean useIndy(InstrumentationModule instrumentationModule) {
    List<TypeInstrumentation> typeInstrumentations = instrumentationModule.typeInstrumentations();
    TypePool typePool = AgentBuilder.PoolStrategy.Default.FAST.typePool(classFileLocator, null);

    for (TypeInstrumentation typeInstrumentation : typeInstrumentations) {
      AtomicInteger inlineCounter = new AtomicInteger();
      AtomicInteger nonInlineCounter = new AtomicInteger();
      typeInstrumentation.transform(
          new TypeTransformer() {
            @Override
            public void applyAdviceToMethod(
                ElementMatcher<? super MethodDescription> methodMatcher,
                Function<Advice.WithCustomMapping, Advice.WithCustomMapping> mappingCustomizer,
                String adviceClassName) {
              TypeDescription type = typePool.describe(adviceClassName).resolve();
              MethodList<MethodDescription.InDefinedShape> methodList = type.getDeclaredMethods();
              for (MethodDescription.InDefinedShape method : methodList) {
                for (AnnotationDescription annotation : method.getDeclaredAnnotations()) {
                  if (Advice.OnMethodEnter.class
                          .getName()
                          .equals(annotation.getAnnotationType().getName())
                      || Advice.OnMethodExit.class
                          .getName()
                          .equals(annotation.getAnnotationType().getName())) {
                    AnnotationValue<?, ?> value = annotation.getValue("inline");
                    if (value.getState().isDefined() && Boolean.FALSE.equals(value.resolve())) {
                      nonInlineCounter.incrementAndGet();
                    } else {
                      inlineCounter.incrementAndGet();
                    }
                  }
                }
              }
            }

            @Override
            public void applyTransformer(AgentBuilder.Transformer transformer) {}
          });

      // While it is possible to use non-inline advice with the non-indy instrumentation, by adding
      // the advice classes as helper classes, we assume that nobody relies on that. Having a
      // non-inline is treated as a marker that the instrumentation can use indy.
      if (nonInlineCounter.get() > 0) {
        return true;
      }
      // Similarly inline advice could be used with indy instrumentation, but we assume that if
      // inline advice is used, then it is not indy ready.
      if (inlineCounter.get() > 0) {
        return false;
      }
    }
    // no advice annotations were used so the instrumentation is using a AgentBuilder.Transformer
    if (instrumentationModule instanceof ExperimentalInstrumentationModule) {
      ExperimentalInstrumentationModule experimentalModule =
          (ExperimentalInstrumentationModule) instrumentationModule;
      // injected class names makes sense only for indy instrumentation
      if (!experimentalModule.injectedClassNames().isEmpty()) {
        return true;
      }
      // TODO: exposed class names could also be used as a hint once
      // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/17765 is merged
    }

    // we aren't able to tell whether the instrumentation is using ready for indy instrumentation or
    // not, so we assume that it is not
    return false;
  }
}
