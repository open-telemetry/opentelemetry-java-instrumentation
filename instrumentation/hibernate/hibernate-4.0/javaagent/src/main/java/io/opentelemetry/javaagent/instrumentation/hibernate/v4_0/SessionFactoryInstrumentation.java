/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.HibernateSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.SharedSessionContract;

public class SessionFactoryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.hibernate.SessionFactory", "org.hibernate.SessionBuilder");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(
        named("org.hibernate.SessionFactory").or(named("org.hibernate.SessionBuilder")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(namedOneOf("openSession", "openStatelessSession"))
            .and(takesArguments(0))
            .and(returns(namedOneOf("org.hibernate.Session", "org.hibernate.StatelessSession"))),
        SessionFactoryInstrumentation.class.getName() + "$SessionFactoryAdvice");
  }

  @SuppressWarnings("unused")
  public static class SessionFactoryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void openSession(@Advice.Return SharedSessionContract session) {

      Context parentContext = Java8BytecodeBridge.currentContext();
      Context context = instrumenter().start(parentContext, "Session");

      VirtualField<SharedSessionContract, Context> virtualField =
          VirtualField.find(SharedSessionContract.class, Context.class);
      virtualField.set(session, context);
    }
  }
}
