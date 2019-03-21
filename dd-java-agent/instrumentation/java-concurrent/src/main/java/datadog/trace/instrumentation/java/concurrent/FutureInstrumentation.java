package datadog.trace.instrumentation.java.concurrent;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public final class FutureInstrumentation extends Instrumenter.Default {

  /**
   * Only apply executor instrumentation to whitelisted executors. In the future, this restriction
   * may be lifted to include all executors.
   */
  private static final Collection<String> WHITELISTED_FUTURES;

  static {
    final String[] whitelist = {
      "java.util.concurrent.ForkJoinTask",
      "java.util.concurrent.CountedCompleter",
      "java.util.concurrent.ForkJoinTask$AdaptedCallable",
      "java.util.concurrent.ForkJoinTask$RunnableExecuteAction",
      "java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask",
      "java.util.concurrent.FutureTask",
      "java.util.concurrent.ExecutorCompletionService$QueueingFuture",
      "java.util.concurrent.RecursiveAction",
      "scala.concurrent.forkjoin.ForkJoinTask",
      "scala.concurrent.impl.ExecutionContextImpl$AdaptedForkJoinTask",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedRunnableAction",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedCallable",
      "scala.concurrent.forkjoin.ForkJoinTask$AdaptedRunnable",
      "scala.collection.parallel.AdaptiveWorkStealingForkJoinTasks$WrappedTask",
      "akka.dispatch.Mailbox",
      "akka.dispatch.forkjoin.ForkJoinTask",
      "akka.dispatch.Mailboxes$$anon$1",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedRunnableAction",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedCallable",
      "akka.dispatch.forkjoin.ForkJoinTask$AdaptedRunnable",
      "akka.dispatch.Dispatcher$$anon$1",
      "akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinTask",
      "com.google.common.util.concurrent.SettableFuture",
      "com.google.common.util.concurrent.AbstractFuture$TrustedFuture",
      "com.google.common.util.concurrent.AbstractFuture",
      "io.netty.util.concurrent.ScheduledFutureTask",
      "com.google.common.util.concurrent.ListenableFutureTask"
    };
    WHITELISTED_FUTURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(whitelist)));
  }

  public FutureInstrumentation() {
    super(AbstractExecutorInstrumentation.EXEC_NAME);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named(Future.class.getName())))
        .and(
            new ElementMatcher<TypeDescription>() {
              @Override
              public boolean matches(final TypeDescription target) {
                final boolean whitelisted = WHITELISTED_FUTURES.contains(target.getName());
                if (!whitelisted) {
                  log.debug("Skipping future instrumentation for {}", target.getName());
                }
                return whitelisted;
              }
            });
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put(Future.class.getName(), State.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("cancel").and(returns(boolean.class)), CanceledFutureAdvice.class.getName());
  }

  public static class CanceledFutureAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(@Advice.This final Future<?> future) {
      // Try to close continuation even if future was not cancelled:
      // the expectation is that continuation should be closed after 'cancel'
      // is called, one way or another
      final ContextStore<Future, State> contextStore =
          InstrumentationContext.get(Future.class, State.class);
      final State state = contextStore.get(future);
      if (state != null) {
        state.closeContinuation();
      }
    }
  }
}
