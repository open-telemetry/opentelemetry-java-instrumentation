/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.wrapContext;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.ConnectionAttempt;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnectOptions;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class ConnectionFactoryInstrumentation implements TypeInstrumentation {

  private static final String VERTX_5_0_CONNECTION_FACTORY =
      "io.vertx.sqlclient.spi.ConnectionFactory";
  private static final String VERTX_5_1_CONNECTION_FACTORY =
      "io.vertx.sqlclient.spi.connection.ConnectionFactory";

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(VERTX_5_0_CONNECTION_FACTORY)
        .or(hasClassesNamed(VERTX_5_1_CONNECTION_FACTORY));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(VERTX_5_0_CONNECTION_FACTORY, VERTX_5_1_CONNECTION_FACTORY);
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("connect")
            .and(takesArguments(2))
            .and(takesArgument(0, named("io.vertx.core.Context")))
            .and(takesArgument(1, named("io.vertx.core.Future")))
            .and(returns(named("io.vertx.core.Future"))),
        getClass().getName() + "$ConnectAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.This Object connectionFactory,
        @Advice.Argument(1) Future<SqlConnectOptions> connectOptionsFuture) {
      Future<SqlConnectOptions> result = connectOptionsFuture;
      ConnectionAttempt connectionAttempt =
          VertxSqlClientSingletons.createConnectionAttempt(connectionFactory, connectOptionsFuture);
      if (connectionAttempt != null) {
        result =
            VertxSqlClientSingletons.captureConnectionAttempt(
                connectOptionsFuture, connectionAttempt);
      }
      return new Object[] {result, connectionAttempt};
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Future<?> onExit(
        @Advice.Return Future<?> future, @Advice.Enter @Nullable Object[] enterState) {
      ConnectionAttempt connectionAttempt =
          enterState != null ? (ConnectionAttempt) enterState[1] : null;
      return connectionAttempt != null
          ? wrapContext(VertxSqlClientSingletons.attachConnectionData(future, connectionAttempt))
          : future;
    }
  }
}
