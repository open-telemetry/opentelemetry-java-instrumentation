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
 * Inspect advice classes used by the given instrumentation module and determine whether it should
 * use the isolated classloader path. The selection is based on the {@code inline} attribute of
 * {@link Advice.OnMethodEnter} and {@link Advice.OnMethodExit} annotations. An explicit helper
 * class strategy can override this selection.
 */
public class AdviceInspector {

  private final ClassFileLocator classFileLocator;

  public AdviceInspector(ClassFileLocator classFileLocator) {
    this.classFileLocator = classFileLocator;
  }

  public boolean useIsolatedAdvice(InstrumentationModule instrumentationModule) {
    Boolean result =
        useIsolatedAdvice(instrumentationModule, adviceClassNames(instrumentationModule));

    // we aren't able to tell whether the instrumentation is ready to use the isolated classloader
    // path, so assume not
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
    // When all advice has inlining disabled, the instrumentation can use the isolated classloader
    // path.
    if (mode == AdviceMode.ALL_NON_INLINE) {
      return true;
    }

    // Without advice annotations, helper registrations indicate that this AgentBuilder.Transformer
    // instrumentation needs isolated helper loading.
    if (!instrumentationModule.injectedClassNames().isEmpty()) {
      return true;
    }
    if (!instrumentationModule.exposedClassNames().isEmpty()) {
      return true;
    }

    // we aren't able to tell whether the instrumentation is ready to use the isolated classloader
    // path
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
            // Helper placement is independent of advice inlining; an explicit helper class strategy
            // can override this inference.
            // Advice with inline = false indicates that the instrumentation can use the isolated
            // classloader path. Advice with inline = true could still use isolated helpers, but we
            // assume that it is not ready to use the isolated classloader path.
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
