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

package io.opentelemetry.instrumentation.auto.opentelemetryapi;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import application.io.grpc.Context;
import application.io.opentelemetry.correlationcontext.CorrelationContext;
import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// TODO: Actually bridge correlation context. We currently just stub out withCorrelationContext
// to have minimum functionality with SDK shim implementations.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/973
@AutoService(Instrumenter.class)
public class CorrelationsContextUtilsInstrumentation extends AbstractInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.correlationcontext.CorrelationsContextUtils");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(isStatic())
            .and(named("withCorrelationContext"))
            .and(takesArguments(2)),
        CorrelationsContextUtilsInstrumentation.class.getName() + "$WithCorrelationContextAdvice");
    return transformers;
  }

  public static class WithCorrelationContextAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) final CorrelationContext applicationCorrelationContext,
        @Advice.Argument(1) final Context applicationContext,
        @Advice.Return(readOnly = false) Context applicationUpdatedContext) {
      applicationUpdatedContext = applicationContext;
    }
  }
}
