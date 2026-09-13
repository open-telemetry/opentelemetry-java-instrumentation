/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientConnectionPoolState.Submission;
import io.vertx.core.internal.pool.ConnectionPool;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SqlConnectionPoolInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.pool.SqlConnectionPool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("execute")
            .and(
                takesArgument(
                    0,
                    namedOneOf(
                        "io.vertx.sqlclient.internal.command.CommandBase",
                        "io.vertx.sqlclient.spi.protocol.CommandBase")))
            .and(takesArgument(1, named("io.vertx.core.Completable"))),
        getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.FieldValue("pool") ConnectionPool<?> pool) {
      VertxSqlClientConnectionPoolState.attachSupplier(pool);
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Submission onEnter(
        @Advice.FieldValue("pool") ConnectionPool<?> pool, @Advice.Argument(0) Object command) {
      return VertxSqlClientConnectionPoolState.enterSubmission(pool, command);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Submission previous) {
      VertxSqlClientConnectionPoolState.setSubmission(previous);
    }
  }
}
