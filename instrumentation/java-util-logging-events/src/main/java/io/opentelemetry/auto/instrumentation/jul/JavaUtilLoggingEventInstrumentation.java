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
package io.opentelemetry.auto.instrumentation.jul;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
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
public class JavaUtilLoggingEventInstrumentation extends Instrumenter.Default {
  public JavaUtilLoggingEventInstrumentation() {
    super("java-util-logging-events");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return safeHasSuperType(named("java.util.logging.Logger"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JavaUtilLoggingEvents",
      packageName + ".JavaUtilLoggingEvents$AccessibleFormatter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(named("log"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("java.util.logging.LogRecord"))),
        JavaUtilLoggingEventInstrumentation.class.getName() + "$LogMessageAdvice");
    return transformers;
  }

  public static class LogMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This final Logger logger, @Advice.Argument(0) final LogRecord logRecord) {
      JavaUtilLoggingEvents.capture(logger, logRecord);
    }
  }
}
