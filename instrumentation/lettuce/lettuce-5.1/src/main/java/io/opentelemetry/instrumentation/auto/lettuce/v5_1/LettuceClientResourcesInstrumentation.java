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

package io.opentelemetry.instrumentation.auto.lettuce.v5_1;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.lettuce.core.resource.DefaultClientResources;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class LettuceClientResourcesInstrumentation extends Instrumenter.Default {

  public LettuceClientResourcesInstrumentation() {
    super("lettuce", "lettuce-5", "lettuce-5.1");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("io.lettuce.core.tracing.Tracing");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("io.lettuce.core.resource.DefaultClientResources");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".OpenTelemetryTracing",
      packageName + ".OpenTelemetryTracing$OpenTelemetryTracerProvider",
      packageName + ".OpenTelemetryTracing$OpenTelemetryTraceContextProvider",
      packageName + ".OpenTelemetryTracing$OpenTelemetryTraceContext",
      packageName + ".OpenTelemetryTracing$OpenTelemetryEndpoint",
      packageName + ".OpenTelemetryTracing$OpenTelemetryTracer",
      packageName + ".OpenTelemetryTracing$OpenTelemetrySpan",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(isStatic()).and(named("builder")),
        LettuceClientResourcesInstrumentation.class.getName() + "$DefaultClientResourcesAdvice");
  }

  public static class DefaultClientResourcesAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodEnter(@Advice.Return DefaultClientResources.Builder builder) {
      builder.tracing(OpenTelemetryTracing.INSTANCE);
    }
  }
}
