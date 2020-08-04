/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.traceannotation;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.api.decorator.config.Config;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TraceConfig Instrumentation does not extend Default.
 *
 * <p>Instead it directly implements Instrumenter#instrument() and adds one default Instrumenter for
 * every configured class+method-list.
 *
 * <p>If this becomes a more common use case the building logic should be abstracted out into a
 * super class.
 */
@AutoService(Instrumenter.class)
public class TraceConfigInstrumentation implements Instrumenter {

  private static final Logger log = LoggerFactory.getLogger(TraceConfigInstrumentation.class);

  private final Map<String, Set<String>> classMethodsToTrace;

  public TraceConfigInstrumentation() {
    classMethodsToTrace = MethodsConfigurationParser.parse(Config.get().getTraceMethods());

    Map<String, Set<String>> excludedMethods =
        MethodsConfigurationParser.parse(Config.get().getTraceAnnotatedMethodsExclude());
    for (Map.Entry<String, Set<String>> entry : excludedMethods.entrySet()) {
      Set<String> tracedMethods = classMethodsToTrace.get(entry.getKey());
      if (tracedMethods != null) {
        tracedMethods.removeAll(entry.getValue());
      }
    }
  }

  @Override
  public AgentBuilder instrument(AgentBuilder agentBuilder) {
    if (classMethodsToTrace.isEmpty()) {
      return agentBuilder;
    }

    for (Map.Entry<String, Set<String>> entry : classMethodsToTrace.entrySet()) {
      TracerClassInstrumentation tracerConfigClass =
          new TracerClassInstrumentation(entry.getKey(), entry.getValue());
      agentBuilder = tracerConfigClass.instrument(agentBuilder);
    }
    return agentBuilder;
  }

  // Not Using AutoService to hook up this instrumentation
  public static class TracerClassInstrumentation extends Default {
    private final String className;
    private final Set<String> methodNames;

    /** No-arg constructor only used by muzzle and tests. */
    public TracerClassInstrumentation() {
      this("io.opentracing.contrib.dropwizard.Trace", Collections.singleton("noop"));
    }

    public TracerClassInstrumentation(final String className, final Set<String> methodNames) {
      super("trace", "trace-config");
      this.className = className;
      this.methodNames = methodNames;
    }

    @Override
    public ElementMatcher<ClassLoader> classLoaderMatcher() {
      // Optimization for expensive typeMatcher.
      return hasClassesNamed(className);
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return safeHasSuperType(named(className));
    }

    @Override
    public String[] helperClassNames() {
      return new String[] {
        packageName + ".TraceDecorator",
      };
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

      return Collections.<ElementMatcher<? super MethodDescription>, String>singletonMap(
          methodMatchers, packageName + ".TraceAdvice");
    }
  }
}
