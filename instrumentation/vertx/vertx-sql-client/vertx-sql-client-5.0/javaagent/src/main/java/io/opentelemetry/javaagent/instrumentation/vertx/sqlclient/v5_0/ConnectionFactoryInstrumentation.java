/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.wrapContext;
import static net.bytebuddy.matcher.ElementMatchers.named;
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
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class ConnectionFactoryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.sqlclient.spi.ConnectionFactory");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.spi.ConnectionFactory");
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

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static ConnectionAttempt onEnter(
        @Advice.This Object connectionFactory,
        @Advice.Argument(value = 1, readOnly = false)
            Future<SqlConnectOptions> connectOptionsFuture) {
      ConnectionAttempt connectionAttempt =
          VertxSqlClientSingletons.createConnectionAttempt(connectionFactory);
      connectOptionsFuture =
          VertxSqlClientSingletons.captureConnectionAttempt(
              connectOptionsFuture, connectionAttempt);
      return connectionAttempt;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Future<?> onExit(
        @Advice.Return Future<?> future,
        @Advice.Enter @Nullable ConnectionAttempt connectionAttempt) {
      return wrapContext(VertxSqlClientSingletons.attachConnectionData(future, connectionAttempt));
    }
  }
}
