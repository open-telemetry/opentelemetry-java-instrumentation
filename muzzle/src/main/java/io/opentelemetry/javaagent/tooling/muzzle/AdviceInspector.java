/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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

/**
 * Inspect advice classes used by the given instrumentation module and determine whether the
 * instrumentation should inject helper classes into the instrumented classloader or load them into
 * separate class loader. The decision is made based on the {@code inline} attribute of {@link
 * Advice.OnMethodEnter} and {@link Advice.OnMethodExit} annotations.
 */
public class AdviceInspector {

  private final ClassFileLocator classFileLocator;

  public AdviceInspector(ClassFileLocator classFileLocator) {
    this.classFileLocator = classFileLocator;
  }

  public boolean useIsolatedAdvice(InstrumentationModule instrumentationModule) {
    Boolean result =
        useIsolatedAdvice(instrumentationModule, adviceClassNames(instrumentationModule));

    // we aren't able to tell whether the instrumentation is ready for isolated advice loading or
    // not, so we assume that it is not
    return result != null ? result : false;
  }

  @Nullable
  public Boolean useIsolatedAdvice(
      InstrumentationModule instrumentationModule, Collection<String> adviceClassNames) {
    AdviceMode mode = inspect(adviceClassNames);
    if (mode == AdviceMode.MIXED) {
      return null;
    }
    if (mode == AdviceMode.ALL_INLINE) {
      return false;
    }
    // Having all advice non-inline is treated as a marker that the instrumentation can use isolated
    // advice loading.
    // Users can override the selection by explicitly choosing how to load helper classes.
    if (mode == AdviceMode.ALL_NON_INLINE) {
      return true;
    }

    // no advice annotations were used so the instrumentation is using an AgentBuilder.Transformer
    // injected class names makes sense only for isolated helper loading
    if (!instrumentationModule.injectedClassNames().isEmpty()) {
      return true;
    }
    // exposed class names makes sense only for isolated helper loading
    if (!instrumentationModule.exposedClassNames().isEmpty()) {
      return true;
    }

    // we aren't able to tell whether the instrumentation is ready for isolated advice loading or
    // not
    return null;
  }

  // visible for testing
  public AdviceMode inspect(InstrumentationModule instrumentationModule) {
    return inspect(adviceClassNames(instrumentationModule));
  }

  private AdviceMode inspect(Collection<String> adviceClassNames) {
    TypePool typePool = AgentBuilder.PoolStrategy.Default.FAST.typePool(classFileLocator, null);

    int inlineCount = 0;
    int nonInlineCount = 0;
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
            // While it is possible to use advice marked inline = false with injected advice
            // classes, we assume that nobody relies on that.
            // Advice marked inline = false is treated as a marker that the instrumentation can use
            // isolated advice loading.
            // Similarly advice marked inline = true could be used with isolated helper loading,
            // but we assume that if inline = true is used, then it is not ready for isolated advice
            // loading.
            if (value.getState().isDefined() && Boolean.FALSE.equals(value.resolve())) {
              nonInlineCount++;
            } else {
              inlineCount++;
            }
          }
        }
      }
    }
    if (inlineCount > 0 && nonInlineCount > 0) {
      return AdviceMode.MIXED;
    }
    if (inlineCount > 0) {
      return AdviceMode.ALL_INLINE;
    }
    if (nonInlineCount > 0) {
      return AdviceMode.ALL_NON_INLINE;
    }
    return AdviceMode.NO_ADVICE;
  }

  private static Set<String> adviceClassNames(InstrumentationModule instrumentationModule) {
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
    return adviceClassNames;
  }

  public enum AdviceMode {
    NO_ADVICE,
    ALL_INLINE,
    ALL_NON_INLINE,
    MIXED
  }
}
