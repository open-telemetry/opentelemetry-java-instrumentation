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

package io.opentelemetry.auto.instrumentation.jul;

import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.bootstrap.instrumentation.logging.LoggerDepth;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class JavaUtilLoggingSpansInstrumentation extends Instrumenter.Default {
  public JavaUtilLoggingSpansInstrumentation() {
    super("java-util-logging");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return extendsClass(named("java.util.logging.Logger"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JavaUtilLoggingSpans",
      packageName + ".JavaUtilLoggingSpans$AccessibleFormatter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(named("log"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("java.util.logging.LogRecord"))),
        JavaUtilLoggingSpansInstrumentation.class.getName() + "$LogAdvice");
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(named("logRaw"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.jboss.logmanager.ExtLogRecord"))),
        JavaUtilLoggingSpansInstrumentation.class.getName() + "$LogAdvice");
    return transformers;
  }

  public static class LogAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean methodEnter(
        @Advice.This final Logger logger, @Advice.Argument(0) final LogRecord logRecord) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      boolean topLevel = CallDepthThreadLocalMap.incrementCallDepth(LoggerDepth.class) == 0;
      if (topLevel) {
        JavaUtilLoggingSpans.capture(logger, logRecord);
      }
      return topLevel;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Enter final boolean topLevel) {
      if (topLevel) {
        CallDepthThreadLocalMap.reset(LoggerDepth.class);
      }
    }
  }
}
