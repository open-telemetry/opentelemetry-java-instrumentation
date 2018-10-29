package datadog.trace.instrumentation.java.concurrent;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public final class ExecutorInstrumentation extends Instrumenter.Default {
  public static final String EXEC_NAME = "java_concurrent";

  /**
   * Only apply executor instrumentation to whitelisted executors. In the future, this restriction
   * may be lifted to include all executors.
   */
  private static final Collection<String> WHITELISTED_EXECUTORS;
  /**
   * Some frameworks have their executors defined as anon classes inside other classes. Referencing
   * anon classes by name would be fragile, so instead we will use list of class prefix names. Since
   * checking this list is more expensive (O(n)) we should try to keep it short.
   */
  private static final Collection<String> WHITELISTED_EXECUTORS_PREFIXES;

  static {
    final String[] whitelist = {
      "java.util.concurrent.AbstractExecutorService",
      "java.util.concurrent.ThreadPoolExecutor",
      "java.util.concurrent.ScheduledThreadPoolExecutor",
      "java.util.concurrent.ForkJoinPool",
      "java.util.concurrent.Executors$FinalizableDelegatedExecutorService",
      "java.util.concurrent.Executors$DelegatedExecutorService",
      "javax.management.NotificationBroadcasterSupport$1",
      "scala.concurrent.Future$InternalCallbackExecutor$",
      "scala.concurrent.impl.ExecutionContextImpl",
      "scala.concurrent.impl.ExecutionContextImpl$$anon$1",
      "scala.concurrent.forkjoin.ForkJoinPool",
      "scala.concurrent.impl.ExecutionContextImpl$$anon$3",
      "akka.dispatch.MessageDispatcher",
      "akka.dispatch.Dispatcher",
      "akka.actor.ActorSystemImpl$$anon$1",
      "akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinPool",
      "akka.dispatch.forkjoin.ForkJoinPool",
      "akka.dispatch.BalancingDispatcher",
      "akka.dispatch.ThreadPoolConfig$ThreadPoolExecutorServiceFactory$$anon$1",
      "akka.dispatch.PinnedDispatcher",
      "akka.dispatch.ExecutionContexts$sameThreadExecutionContext$",
      "play.api.libs.streams.Execution$trampoline$",
      "io.netty.channel.MultithreadEventLoopGroup",
      "io.netty.util.concurrent.MultithreadEventExecutorGroup",
      "io.netty.util.concurrent.AbstractEventExecutorGroup",
      "io.netty.channel.epoll.EpollEventLoopGroup",
      "io.netty.channel.nio.NioEventLoopGroup",
      "io.netty.util.concurrent.GlobalEventExecutor",
      "io.netty.util.concurrent.AbstractScheduledEventExecutor",
      "io.netty.util.concurrent.AbstractEventExecutor",
      "io.netty.util.concurrent.SingleThreadEventExecutor",
      "io.netty.channel.nio.NioEventLoop",
      "io.netty.channel.SingleThreadEventLoop",
    };
    WHITELISTED_EXECUTORS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(whitelist)));

    final String[] whitelistPrefixes = {"slick.util.AsyncExecutor$"};
    WHITELISTED_EXECUTORS_PREFIXES =
        Collections.unmodifiableCollection(Arrays.asList(whitelistPrefixes));
  }

  public ExecutorInstrumentation() {
    super(EXEC_NAME);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named(Executor.class.getName())))
        .and(
            new ElementMatcher<TypeDescription>() {
              @Override
              public boolean matches(final TypeDescription target) {
                boolean whitelisted = WHITELISTED_EXECUTORS.contains(target.getName());

                // Check for possible prefixes match only if not whitelisted already
                if (!whitelisted) {
                  for (final String name : WHITELISTED_EXECUTORS_PREFIXES) {
                    if (target.getName().startsWith(name)) {
                      whitelisted = true;
                      break;
                    }
                  }
                }

                if (!whitelisted) {
                  log.debug("Skipping executor instrumentation for {}", target.getName());
                }
                return whitelisted;
              }
            });
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      ExecutorInstrumentation.class.getName() + "$ConcurrentUtils",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put("java.lang.Runnable", State.class.getName());
    map.put("java.util.concurrent.Callable", State.class.getName());
    map.put("java.util.concurrent.Future", State.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        named("execute").and(takesArgument(0, Runnable.class)),
        SetRunnableStateAdvice.class.getName());
    transformers.put(
        named("submit").and(takesArgument(0, Runnable.class)),
        SetRunnableStateAdvice.class.getName());
    transformers.put(
        named("submit").and(takesArgument(0, Callable.class)),
        SetCallableStateAdvice.class.getName());
    transformers.put(
        nameMatches("invoke(Any|All)$").and(takesArgument(0, Callable.class)),
        SetCallableStateForCallableCollectionAdvice.class.getName());
    return transformers;
  }

  public static class SetRunnableStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterJobSubmit(
        @Advice.This final Executor executor, @Advice.Argument(value = 0) final Runnable task) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (ConcurrentUtils.shouldAttachStateToTask(task, executor)) {
        final ContextStore<Runnable, State> contextStore =
            InstrumentationContext.get(Runnable.class, State.class);
        return ConcurrentUtils.setupState(contextStore, task, (TraceScope) scope);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.This final Executor executor,
        @Advice.Enter final State state,
        @Advice.Thrown final Throwable throwable) {
      ConcurrentUtils.cleanUpOnMethodExit(executor, state, throwable);
    }
  }

  public static class SetCallableStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterJobSubmit(
        @Advice.This final Executor executor, @Advice.Argument(value = 0) final Callable task) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (ConcurrentUtils.shouldAttachStateToTask(task, executor)) {
        final ContextStore<Callable, State> contextStore =
            InstrumentationContext.get(Callable.class, State.class);
        return ConcurrentUtils.setupState(contextStore, task, (TraceScope) scope);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.This final Executor executor,
        @Advice.Enter final State state,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return final Future future) {
      if (state != null && future != null) {
        final ContextStore<Future, State> contextStore =
            InstrumentationContext.get(Future.class, State.class);
        contextStore.put(future, state);
      }
      ConcurrentUtils.cleanUpOnMethodExit(executor, state, throwable);
    }
  }

  public static class SetCallableStateForCallableCollectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapJob(
        @Advice.This final Executor executor,
        @Advice.Argument(value = 0) final Collection<? extends Callable<?>> tasks) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (scope instanceof TraceScope
          && ((TraceScope) scope).isAsyncPropagating()
        && tasks != null) {
        for (final Callable<?> task : tasks) {
          if (task != null) {
            final ContextStore<Callable, State> contextStore =
                InstrumentationContext.get(Callable.class, State.class);
            ConcurrentUtils.setupState(contextStore, task, (TraceScope) scope);
          }
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void checkCancel(
        @Advice.This final Executor executor,
        @Advice.Argument(value = 0) final Collection<? extends Callable<?>> tasks,
        @Advice.Thrown final Throwable throwable) {
      /*
       Note1: invokeAny doesn't return any futures so all we need to do for it
       is to make sure we close all scopes in case of an exception.
       Note2: invokeAll does return futures - but according to its documentation
       it actually only returns after all futures have been completed - i.e. it blocks.
       This means we do not need to setup any hooks on these futures, we just need to clean
       up any continuations in case of an error.
       (according to ExecutorService docs and AbstractExecutorService code)
      */
      if (null != throwable) {
        for (final Callable<?> task : tasks) {
          if (task != null) {
            final ContextStore<Callable, State> contextStore =
                InstrumentationContext.get(Callable.class, State.class);
            final State state = contextStore.get(task);
            if (state != null) {
              // Note: this may potentially close somebody else's continuation if we didn't set it up
              // in setupState because it was already present before us. This should be safe but may lead
              // to non-attributed async work in some very rare cases.
              // Alternative is to not close continuation here if we did not set it up in setupState but
              // this may potentially lead to memory leaks if callers do not properly handle exceptions.
              state.closeContinuation();
            }
          }
        }
      }
    }
  }

  /** Utils for concurrent instrumentations. */
  @Slf4j
  public static class ConcurrentUtils {

    /**
     * Checks if given task should get state attached.
     *
     * <p>Warning: this also increments nested call counter!
     *
     * @param task task object
     * @param executor executor this task was scheduled on
     * @return true iff given task object should be wrapped
     */
    public static boolean shouldAttachStateToTask(final Object task, final Executor executor) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      return (scope instanceof TraceScope
          && ((TraceScope) scope).isAsyncPropagating()
        && task != null);
    }

    /**
     * Create task state given current scope
     *
     * @param contextStore context storage
     * @param task task instance
     * @param scope current scope
     * @param <T> task class type
     * @return new state
     */
    public static <T> State setupState(
        final ContextStore<T, State> contextStore, final T task, final TraceScope scope) {
      final State state = contextStore.putIfAbsent(task, State.FACTORY);
      final TraceScope.Continuation continuation = scope.capture();
      if (state.setContinuation(continuation)) {
        log.debug("created continuation {} from scope {}, state: {}", continuation, scope, state);
      } else {
        continuation.close();
      }
      return state;
    }

    /**
     * Clean up after job submission method has exited
     *
     * @param executor the current executor
     * @param state task instrumentation state
     * @param throwable throwable that may have been thrown
     */
    public static void cleanUpOnMethodExit(
        final Executor executor, final State state, final Throwable throwable) {
      if (null != state && null != throwable) {
        // Note: this may potentially close somebody else's continuation if we didn't set it up
        // in setupState because it was already present before us. This should be safe but may lead
        // to non-attributed async work in some very rare cases.
        // Alternative is to not close continuation here if we did not set it up in setupState but
        // this may potentially lead to memory leaks if callers do not properly handle exceptions.
        state.closeContinuation();
      }
    }
  }
}
