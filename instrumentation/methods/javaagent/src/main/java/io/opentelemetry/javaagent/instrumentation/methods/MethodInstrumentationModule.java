/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.config.MethodsConfigurationParser;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * TraceConfig Instrumentation does not extend Default.
 *
 * <p>Instead it directly implements Instrumenter#instrument() and adds one default Instrumenter for
 * every configured class+method-list.
 *
 * <p>If this becomes a more common use case the building logic should be abstracted out into a
 * super class.
 */
@AutoService(InstrumentationModule.class)
public class MethodInstrumentationModule extends InstrumentationModule {

  private static final String TRACE_METHODS_CONFIG = "otel.instrumentation.methods.include";

  private final List<TypeInstrumentation> typeInstrumentations;

  public MethodInstrumentationModule() {
    super("methods");

    Map<String, Set<String>> classMethodsToTrace =
        MethodsConfigurationParser.parse(Config.get().getProperty(TRACE_METHODS_CONFIG));

    typeInstrumentations =
        classMethodsToTrace.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .map(e -> new TracerClassInstrumentation(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
  }

  // the default configuration has empty "otel.instrumentation.methods.include", and so doesn't
  // generate any TypeInstrumentation for muzzle to analyze
  @Override
  protected String[] additionalHelperClassNames() {
    return typeInstrumentations.isEmpty()
        ? new String[0]
        : new String[] {"io.opentelemetry.javaagent.instrumentation.methods.MethodTracer"};
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return typeInstrumentations;
  }

  public static class TracerClassInstrumentation implements TypeInstrumentation {
    private final String className;
    private final Set<String> methodNames;

    public TracerClassInstrumentation(String className, Set<String> methodNames) {
      this.className = className;
      this.methodNames = methodNames;
    }

    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed(className);
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return safeHasSuperType(named(className));
    }

    @Override
    public Map<ElementMatcher<? super MethodDescription>, String> transformers() {
      ElementMatcher.Junction<MethodDescription> methodMatchers = null;
      for (String methodName : methodNames) {
        if (methodMatchers == null) {
          methodMatchers = named(methodName);
        } else {
          methodMatchers = methodMatchers.or(named(methodName));
        }
      }

      return Collections.singletonMap(methodMatchers, MethodAdvice.class.getName());
    }
  }
}
