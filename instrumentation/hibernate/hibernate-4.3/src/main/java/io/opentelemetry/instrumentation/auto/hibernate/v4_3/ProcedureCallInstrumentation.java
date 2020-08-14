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

package io.opentelemetry.instrumentation.auto.hibernate.v4_3;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.auto.hibernate.SessionMethodUtils;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.procedure.ProcedureCall;

@AutoService(Instrumenter.class)
public class ProcedureCallInstrumentation extends Instrumenter.Default {

  public ProcedureCallInstrumentation() {
    super("hibernate", "hibernate-core");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("org.hibernate.procedure.ProcedureCall", Span.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.auto.hibernate.SessionMethodUtils",
      "io.opentelemetry.instrumentation.auto.hibernate.HibernateDecorator",
    };
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.hibernate.Session");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.hibernate.procedure.ProcedureCall"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("getOutputs")),
        ProcedureCallInstrumentation.class.getName() + "$ProcedureCallMethodAdvice");
  }

  public static class ProcedureCallMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope startMethod(
        @Advice.This final ProcedureCall call, @Advice.Origin("#m") final String name) {

      ContextStore<ProcedureCall, Span> contextStore =
          InstrumentationContext.get(ProcedureCall.class, Span.class);

      return SessionMethodUtils.startScopeFrom(
          contextStore, call, "ProcedureCall." + name, call.getProcedureName(), true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      SessionMethodUtils.closeScope(spanWithScope, throwable, null, null);
    }
  }
}
