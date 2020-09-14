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

package io.opentelemetry.instrumentation.auto.log4j.v2_13_2;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.log4j.v2_13_2.OpenTelemetryContextDataProvider;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.logging.log4j.core.util.ContextDataProvider;

@AutoService(Instrumenter.class)
public final class Log4j2Instrumentation extends Instrumenter.Default {
  public Log4j2Instrumentation() {
    super("log4j2", "log4j");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.logging.log4j.core.util.ContextDataProvider");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.logging.log4j.core.impl.ThreadContextDataInjector");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.log4j.v2_13_2.OpenTelemetryContextDataProvider"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod()
            .and(isPrivate())
            .and(isStatic())
            .and(named("getProviders"))
            .and(takesArguments(0)),
        Log4j2Instrumentation.class.getName() + "$GetProvidersAdvice");
  }

  public static class GetProvidersAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Return(typing = Typing.DYNAMIC, readOnly = false)
            List<ContextDataProvider> providers) {
      // check if already instrumented
      for (ContextDataProvider provider : providers) {
        if (provider instanceof OpenTelemetryContextDataProvider) {
          return;
        }
      }

      List<ContextDataProvider> instrumentedProviders = new ArrayList<>(providers.size() + 1);
      instrumentedProviders.addAll(providers);
      instrumentedProviders.add(new OpenTelemetryContextDataProvider());
      providers = instrumentedProviders;
    }
  }
}
