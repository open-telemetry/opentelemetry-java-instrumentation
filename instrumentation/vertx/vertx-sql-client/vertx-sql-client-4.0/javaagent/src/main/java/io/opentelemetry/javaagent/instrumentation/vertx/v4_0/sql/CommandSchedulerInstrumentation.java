/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.sqlclient.impl.command.CommandBase;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Propagates OpenTelemetry context through the Vert.x SQL client connection pool.
 *
 * <p>When a query is initiated (e.g. {@code pool.query("SELECT ...").execute()}), the correct
 * OpenTelemetry context is active on the event loop thread. The pool may queue the request and
 * dispatch it later when a connection becomes available — by which time the event loop may be
 * handling a different request with a different context.
 *
 * <p>This instrumentation captures the context on the first {@code CommandScheduler.schedule()}
 * call (from the query executor, with the correct context) and restores it on subsequent calls
 * (from the pool to the connection, where the context may be stale). This ensures that downstream
 * instrumentation (e.g. JDBC) on worker threads sees the correct parent context.
 */
public class CommandSchedulerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.vertx.sqlclient.impl.command.CommandScheduler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("schedule")
            .and(takesArgument(0, named("io.vertx.core.impl.ContextInternal")))
            .and(takesArgument(1, named("io.vertx.sqlclient.impl.command.CommandBase"))),
        CommandSchedulerInstrumentation.class.getName() + "$ScheduleAdvice");
  }

  // VirtualField.find requires the raw type; CommandBase is invoked reflectively by ByteBuddy
  @SuppressWarnings({"unused", "rawtypes"})
  public static class ScheduleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Nullable
    public static Scope onEnter(@Advice.Argument(1) CommandBase<?> command) {
      VirtualField<CommandBase, Context> contextField =
          VirtualField.find(CommandBase.class, Context.class);
      Context stored = contextField.get(command);
      if (stored == null) {
        // First schedule call (query executor → pool or direct connection).
        // The current OpenTelemetry context is correct — store it on the command.
        contextField.set(command, Context.current());
        return null;
      }
      // Subsequent schedule call (pool → connection).
      // Restore the stored context so that executeBlocking dispatches with
      // the correct parent for downstream instrumentation (e.g. JDBC).
      return stored.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
