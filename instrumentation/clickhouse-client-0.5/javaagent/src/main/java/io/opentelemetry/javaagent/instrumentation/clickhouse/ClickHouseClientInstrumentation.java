/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.clickhouse.ClickHouseSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.clickhouse.client.ClickHouseClient;
import com.clickhouse.client.ClickHouseRequest;
import com.clickhouse.client.config.ClickHouseDefaults;
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
    return implementsInterface(named("com.clickhouse.client.ClickHouseClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(namedOneOf("executeAndWait", "execute"))
            .and(takesArgument(0, named("com.clickhouse.client.ClickHouseRequest"))),
        this.getClass().getName() + "$ClickHouseExecuteAndWaitAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClickHouseExecuteAndWaitAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) ClickHouseRequest<?> clickHouseRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {

      callDepth = CallDepth.forClass(ClickHouseClient.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      if (clickHouseRequest == null) {
        return;
      }

      Context parentContext = currentContext();

      ClickHouseDbRequest request =
          ClickHouseDbRequest.create(
              clickHouseRequest.getServer().getHost(),
              clickHouseRequest.getServer().getPort(),
              clickHouseRequest
                  .getServer()
                  .getDatabase()
                  .orElse(ClickHouseDefaults.DATABASE.getDefaultValue().toString()),
              clickHouseRequest.getPreparedQuery().getOriginalQuery());

      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") ClickHouseDbRequest clickHouseRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {

      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (scope == null) {
        return;
      }

      scope.close();
      instrumenter().end(context, clickHouseRequest, null, throwable);
    }
  }
}
