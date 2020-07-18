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

package io.opentelemetry.auto.instrumentation.log4j.v2_0;

import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;

@AutoService(Instrumenter.class)
public class Log4jSpansInstrumentation extends Instrumenter.Default {
  public Log4jSpansInstrumentation() {
    super("log4j");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.logging.log4j.spi.AbstractLogger"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".Log4jSpans"};
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(named("logMessage"))
            .and(takesArguments(5))
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("org.apache.logging.log4j.Level")))
            .and(takesArgument(2, named("org.apache.logging.log4j.Marker")))
            .and(takesArgument(3, named("org.apache.logging.log4j.message.Message")))
            .and(takesArgument(4, named("java.lang.Throwable"))),
        Log4jSpansInstrumentation.class.getName() + "$LogMessageAdvice");
    // log4j 2.12.1 introduced and started using this new log() method
    transformers.put(
        isMethod()
            .and(isProtected())
            .and(named("log"))
            .and(takesArguments(6))
            .and(takesArgument(0, named("org.apache.logging.log4j.Level")))
            .and(takesArgument(1, named("org.apache.logging.log4j.Marker")))
            .and(takesArgument(2, named("java.lang.String")))
            .and(takesArgument(3, named("java.lang.StackTraceElement")))
            .and(takesArgument(4, named("org.apache.logging.log4j.message.Message")))
            .and(takesArgument(5, named("java.lang.Throwable"))),
        Log4jSpansInstrumentation.class.getName() + "$LogAdvice");
    return transformers;
  }

  public static class LogMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean methodEnter(
        @Advice.This final Logger logger,
        @Advice.Argument(1) final Level level,
        @Advice.Argument(3) final Message message,
        @Advice.Argument(4) final Throwable t) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      boolean topLevel = CallDepthThreadLocalMap.incrementCallDepth(LoggerDepth.class) == 0;
      if (topLevel) {
        Log4jSpans.capture(logger, level, message, t);
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

  public static class LogAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean methodEnter(
        @Advice.This final Logger logger,
        @Advice.Argument(0) final Level level,
        @Advice.Argument(4) final Message message,
        @Advice.Argument(5) final Throwable t) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      boolean topLevel = CallDepthThreadLocalMap.incrementCallDepth(LoggerDepth.class) == 0;
      if (topLevel) {
        Log4jSpans.capture(logger, level, message, t);
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
