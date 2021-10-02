/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.PropagatedContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Future;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FutureInstrumentation implements TypeInstrumentation {
  private static final Logger logger = LoggerFactory.getLogger(FutureInstrumentation.class);

  /**
   * Only apply executor instrumentation to allowed executors. In the future, this restriction may
   * be lifted to include all executors.
   */
  private static final Collection<String> ALLOWED_FUTURES;

  static {
    String[] allowed = {
      "akka.dispatch.forkjoin.ForkJoinTask",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedCallable",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedRunnable",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedRunnableAction",
      "akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinTask",
      "akka.dispatch.Mailbox",
      "com.google.common.util.concurrent.AbstractFuture",
      "com.google.common.util.concurrent.AbstractFuture$TrustedFuture",
      "com.google.common.util.concurrent.ListenableFutureTask",
      "com.google.common.util.concurrent.SettableFuture",
      "io.netty.util.concurrent.CompleteFuture",
      "io.netty.util.concurrent.FailedFuture",
      "io.netty.util.concurrent.ScheduledFutureTask",
      "java.util.concurrent.CompletableFuture$BiApply",
      "java.util.concurrent.CompletableFuture$BiCompletion",
      "java.util.concurrent.CompletableFuture$BiRelay",
      "java.util.concurrent.CompletableFuture$ThreadPerTaskExecutor",
      "java.util.concurrent.CountedCompleter",
      "java.util.concurrent.ExecutorCompletionService$QueueingFuture",
      "java.util.concurrent.ForkJoinTask",
      "java.util.concurrent.ForkJoinTask$AdaptedCallable",
      "java.util.concurrent.ForkJoinTask$RunnableExecuteAction",
      "java.util.concurrent.FutureTask",
      "java.util.concurrent.RecursiveAction",
      "java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask",
      "scala.collection.parallel.AdaptiveWorkStealingForkJoinTasks$WrappedTask",
      "scala.concurrent.forkjoin.ForkJoinTask",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedCallable",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedRunnable",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedRunnableAction",
      "scala.concurrent.impl.ExecutionContextImpl$AdaptedForkJoinTask",
    };
    ALLOWED_FUTURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(allowed)));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    ElementMatcher.Junction<TypeDescription> hasFutureInterfaceMatcher =
        implementsInterface(named(Future.class.getName()));
    return new ElementMatcher.Junction.AbstractBase<TypeDescription>() {
      @Override
      public boolean matches(TypeDescription target) {
        boolean allowed = ALLOWED_FUTURES.contains(target.getName());
        if (!allowed && logger.isDebugEnabled() && hasFutureInterfaceMatcher.matches(target)) {
          logger.debug("Skipping future instrumentation for {}", target.getName());
        }
        return allowed;
      }
    }.and(hasFutureInterfaceMatcher); // Apply expensive matcher last.
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("cancel").and(returns(boolean.class)),
        FutureInstrumentation.class.getName() + "$CanceledFutureAdvice");
  }

  @SuppressWarnings("unused")
  public static class CanceledFutureAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(@Advice.This Future<?> future) {
      // Try to clear parent span even if future was not cancelled:
      // the expectation is that parent span should be cleared after 'cancel'
      // is called, one way or another
      VirtualField<Future<?>, PropagatedContext> virtualField =
          VirtualField.find(Future.class, PropagatedContext.class);
      PropagatedContext propagatedContext = virtualField.get(future);
      if (propagatedContext != null) {
        propagatedContext.clear();
      }
    }
  }
}
