/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.clickhouse.ClickHouseSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.clickhouse.client.ClickHouseRequest;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ClickHouseClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.clickhouse.client.ClickHouseRequest");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("query")).and(takesArgument(0, named("java.lang.String"))),
        this.getClass().getName() + "$ClickHouseQueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClickHouseQueryAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This ClickHouseRequest<ClickHouseRequest.Mutation> clickHouseRequest,
        @Advice.Argument(0) String query,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (query == null) {
        return;
      }

      System.out.println("Query: " + query);

      Context parentContext = currentContext();

      ClickHouseDbRequest request =
          ClickHouseDbRequest.create(
              clickHouseRequest.getServer().getHost(),
              clickHouseRequest.getServer().getPort(),
              clickHouseRequest.getServer().getDatabase().get(),
              query);

      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") ClickHouseDbRequest clickHouseRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }

      scope.close();
      instrumenter().end(context, clickHouseRequest, null, throwable);
    }
  }
}
