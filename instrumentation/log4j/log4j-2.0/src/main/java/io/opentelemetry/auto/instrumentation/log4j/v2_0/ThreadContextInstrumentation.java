/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.log4j.v2_0;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.auto.tooling.log.LogContextScopeListener;
import java.lang.reflect.Method;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// FIXME this instrumentation relied on scope listener
// @AutoService(Instrumenter.class)
public class ThreadContextInstrumentation extends Instrumenter.Default {

  public ThreadContextInstrumentation() {
    super("log4j");
  }

  @Override
  protected boolean defaultEnabled() {
    return Config.get().isLogInjectionEnabled();
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.logging.log4j.ThreadContext");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isTypeInitializer(), ThreadContextInstrumentation.class.getName() + "$ThreadContextAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {LogContextScopeListener.class.getName()};
  }

  public static class ThreadContextAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void mdcClassInitialized(@Advice.Origin final Class threadClass) {
      try {
        final Method putMethod = threadClass.getMethod("put", String.class, String.class);
        final Method removeMethod = threadClass.getMethod("remove", String.class);
        // FIXME this instrumentation relied on scope listener
        // GlobalTracer.get().addScopeListener(new LogContextScopeListener(putMethod,
        // removeMethod));
      } catch (final NoSuchMethodException e) {
        org.slf4j.LoggerFactory.getLogger(threadClass)
            .debug("Failed to add log4j ThreadContext span listener", e);
      }
    }
  }
}
