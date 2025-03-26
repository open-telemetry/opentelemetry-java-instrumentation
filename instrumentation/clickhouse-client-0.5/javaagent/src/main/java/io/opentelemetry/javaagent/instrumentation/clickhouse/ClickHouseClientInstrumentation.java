/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.clickhouse.client.ClickHouseClient;
import com.clickhouse.client.ClickHouseRequest;
import com.clickhouse.client.ClickHouseRequestAccess;
import com.clickhouse.client.config.ClickHouseDefaults;
import io.opentelemetry.context.Context;
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
    public static ClickHouseScope onEnter(
        @Advice.Argument(0) ClickHouseRequest<?> clickHouseRequest) {

      CallDepth callDepth = CallDepth.forClass(ClickHouseClient.class);
      if (callDepth.getAndIncrement() > 0 || clickHouseRequest == null) {
        return null;
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
              ClickHouseRequestAccess.getQuery(clickHouseRequest));

      return ClickHouseScope.start(parentContext, request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable, @Advice.Enter ClickHouseScope scope) {

      CallDepth callDepth = CallDepth.forClass(ClickHouseClient.class);
      if (callDepth.decrementAndGet() > 0 || scope == null) {
        return;
      }

      scope.end(throwable);
    }
  }
}
