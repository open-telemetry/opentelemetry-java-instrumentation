/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
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

public class AdviceInspector {

  private final ClassFileLocator classFileLocator;

  public AdviceInspector(ClassFileLocator classFileLocator) {
    this.classFileLocator = classFileLocator;
  }

  public boolean useIsolatedAdvice(InstrumentationModule instrumentationModule) {
    Set<String> adviceClassNames = new HashSet<>();
    for (TypeInstrumentation typeInstrumentation : instrumentationModule.typeInstrumentations()) {
      typeInstrumentation.transform(
          new TypeTransformer() {
            @Override
            public void applyAdviceToMethod(
                ElementMatcher<? super MethodDescription> methodMatcher,
                Function<Advice.WithCustomMapping, Advice.WithCustomMapping> mappingCustomizer,
                String adviceClassName) {
              adviceClassNames.add(adviceClassName);
            }

            @Override
            public void applyTransformer(AgentBuilder.Transformer transformer) {}
          });
    }
    Boolean result = useIsolatedAdvice(instrumentationModule, adviceClassNames);

    // we aren't able to tell whether the instrumentation is using ready for indy instrumentation or
    // not, so we assume that it is not
    return result != null ? result : false;
  }

  @Nullable
  public Boolean useIsolatedAdvice(
      InstrumentationModule instrumentationModule, Collection<String> adviceClassNames) {
    TypePool typePool = AgentBuilder.PoolStrategy.Default.FAST.typePool(classFileLocator, null);

    for (String adviceClassName : adviceClassNames) {
      TypeDescription type = typePool.describe(adviceClassName).resolve();
      MethodList<MethodDescription.InDefinedShape> methodList = type.getDeclaredMethods();
      for (MethodDescription.InDefinedShape method : methodList) {
        for (AnnotationDescription annotation : method.getDeclaredAnnotations()) {
          if (Advice.OnMethodEnter.class.getName().equals(annotation.getAnnotationType().getName())
              || Advice.OnMethodExit.class
                  .getName()
                  .equals(annotation.getAnnotationType().getName())) {
            AnnotationValue<?, ?> value = annotation.getValue("inline");
            // While it is possible to use non-inline advice with the non-indy instrumentation, by
            // adding the advice classes as helper classes, we assume that nobody relies on that.
            // Having a non-inline advice is treated as a marker that the instrumentation can use
            // indy.
            // Similarly inline advice could be used with indy instrumentation, but we assume that
            // if inline advice is used, then it is not indy ready.
            return value.getState().isDefined() && Boolean.FALSE.equals(value.resolve());
          }
        }
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
      if (!experimentalModule.exposedClassNames().isEmpty()) {
        return true;
      }
    }

    // we aren't able to tell whether the instrumentation is using ready for indy instrumentation or
    // not
    return null;
  }
}
