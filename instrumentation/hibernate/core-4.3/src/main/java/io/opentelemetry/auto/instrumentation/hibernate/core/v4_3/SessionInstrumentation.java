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
package io.opentelemetry.auto.instrumentation.hibernate.core.v4_3;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.hibernate.SessionMethodUtils;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.SharedSessionContract;
import org.hibernate.procedure.ProcedureCall;

@AutoService(Instrumenter.class)
public class SessionInstrumentation extends Instrumenter.Default {

  public SessionInstrumentation() {
    super("hibernate", "hibernate-core");
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.SharedSessionContract", Span.class.getName());
    map.put("org.hibernate.procedure.ProcedureCall", Span.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.instrumentation.hibernate.SessionMethodUtils",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.DatabaseClientDecorator",
      "io.opentelemetry.auto.decorator.OrmClientDecorator",
      "io.opentelemetry.auto.instrumentation.hibernate.HibernateDecorator",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasInterface(named("org.hibernate.SharedSessionContract")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(returns(safeHasInterface(named("org.hibernate.procedure.ProcedureCall")))),
        SessionInstrumentation.class.getName() + "$GetProcedureCallAdvice");
  }

  public static class GetProcedureCallAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getProcedureCall(
        @Advice.This final SharedSessionContract session,
        @Advice.Return final ProcedureCall returned) {

      final ContextStore<SharedSessionContract, Span> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, Span.class);
      final ContextStore<ProcedureCall, Span> returnedContextStore =
          InstrumentationContext.get(ProcedureCall.class, Span.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, returnedContextStore, returned);
    }
  }
}
