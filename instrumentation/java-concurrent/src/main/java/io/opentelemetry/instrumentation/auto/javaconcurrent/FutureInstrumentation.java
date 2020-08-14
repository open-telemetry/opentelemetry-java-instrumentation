/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.javaconcurrent;

import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import io.opentelemetry.instrumentation.auto.api.concurrent.State;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Future;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(Instrumenter.class)
public final class FutureInstrumentation extends Instrumenter.Default {

  private static final Logger log = LoggerFactory.getLogger(FutureInstrumentation.class);

  /**
   * Only apply executor instrumentation to whitelisted executors. In the future, this restriction
   * may be lifted to include all executors.
   */
  private static final Collection<String> WHITELISTED_FUTURES;

  static {
    String[] whitelist = {
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
    WHITELISTED_FUTURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(whitelist)));
  }

  public FutureInstrumentation() {
    super(AbstractExecutorInstrumentation.EXEC_NAME);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    final ElementMatcher.Junction<TypeDescription> hasFutureInterfaceMatcher =
        implementsInterface(named(Future.class.getName()));
    return new ElementMatcher.Junction.AbstractBase<TypeDescription>() {
      @Override
      public boolean matches(final TypeDescription target) {
        boolean whitelisted = WHITELISTED_FUTURES.contains(target.getName());
        if (!whitelisted && log.isDebugEnabled() && hasFutureInterfaceMatcher.matches(target)) {
          log.debug("Skipping future instrumentation for {}", target.getName());
        }
        return whitelisted;
      }
    }.and(hasFutureInterfaceMatcher); // Apply expensive matcher last.
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(Future.class.getName(), State.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("cancel").and(returns(boolean.class)),
        FutureInstrumentation.class.getName() + "$CanceledFutureAdvice");
  }

  public static class CanceledFutureAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(@Advice.This final Future<?> future) {
      // Try to clear parent span even if future was not cancelled:
      // the expectation is that parent span should be cleared after 'cancel'
      // is called, one way or another
      ContextStore<Future, State> contextStore =
          InstrumentationContext.get(Future.class, State.class);
      State state = contextStore.get(future);
      if (state != null) {
        state.clearParentContext();
      }
    }
  }
}
