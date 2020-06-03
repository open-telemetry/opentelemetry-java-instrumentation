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
package io.opentelemetry.auto.instrumentation.log4j.v1_1;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.log4j.Category;
import org.apache.log4j.Priority;

@AutoService(Instrumenter.class)
public class Log4jSpansInstrumentation extends Instrumenter.Default {
  public Log4jSpansInstrumentation() {
    super("log4j");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.log4j.Category");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".Log4jSpans"};
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isProtected())
            .and(named("forcedLog"))
            .and(takesArguments(4))
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("org.apache.log4j.Priority")))
            .and(takesArgument(2, named("java.lang.Object")))
            .and(takesArgument(3, named("java.lang.Throwable"))),
        Log4jSpansInstrumentation.class.getName() + "$ForcedLogAdvice");
    return transformers;
  }

  public static class ForcedLogAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean methodEnter(
        @Advice.This final Category logger,
        @Advice.Argument(1) final Priority level,
        @Advice.Argument(2) final Object message,
        @Advice.Argument(3) final Throwable t) {
      // need to track call depth across all loggers to avoid double capture when one logging
      // framework delegates to another
      final boolean topLevel =
          CallDepthThreadLocalMap.incrementCallDepth(java.util.logging.Logger.class) == 0;
      if (topLevel) {
        Log4jSpans.capture(logger, level, message, t);
      }
      return topLevel;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Enter final boolean topLevel) {
      if (topLevel) {
        CallDepthThreadLocalMap.reset(java.util.logging.Logger.class);
      }
    }
  }
}
