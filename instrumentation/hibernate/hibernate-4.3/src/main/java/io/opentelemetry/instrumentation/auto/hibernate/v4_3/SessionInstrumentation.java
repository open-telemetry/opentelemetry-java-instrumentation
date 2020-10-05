/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.hibernate.v4_3;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.hasInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import io.opentelemetry.instrumentation.auto.hibernate.SessionMethodUtils;
import io.opentelemetry.javaagent.tooling.Instrumenter;
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
    Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.SharedSessionContract", Context.class.getName());
    map.put("org.hibernate.procedure.ProcedureCall", Context.class.getName());
    return Collections.unmodifiableMap(map);
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
    return implementsInterface(named("org.hibernate.SharedSessionContract"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(returns(hasInterface(named("org.hibernate.procedure.ProcedureCall")))),
        SessionInstrumentation.class.getName() + "$GetProcedureCallAdvice");
  }

  public static class GetProcedureCallAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getProcedureCall(
        @Advice.This SharedSessionContract session, @Advice.Return ProcedureCall returned) {

      ContextStore<SharedSessionContract, Context> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, Context.class);
      ContextStore<ProcedureCall, Context> returnedContextStore =
          InstrumentationContext.get(ProcedureCall.class, Context.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, returnedContextStore, returned);
    }
  }
}
