/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.datasource;

import static io.opentelemetry.instrumentation.jdbc.internal.DataSourceSingletons.instrumenter;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.sql.DataSource;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DataSourceInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.sql.DataSource"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getConnection"), DataSourceInstrumentation.class.getName() + "$GetConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void start(
        @Advice.This DataSource ds,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!Java8BytecodeBridge.spanFromContext(parentContext).getSpanContext().isValid()) {
        // this instrumentation is already very noisy, and calls to getConnection outside of an
        // existing trace do not tend to be very interesting
        return;
      }

      context = instrumenter().start(parentContext, ds);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This DataSource ds,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();
      instrumenter().end(context, ds, null, throwable);
    }
  }
}
