package datadog.trace.instrumentation.java.concurrent;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
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
      "akka.dispatch.Dispatcher$LazyExecutorServiceDelegate",
      "akka.actor.ActorSystemImpl$$anon$1",
      "akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinPool",
      "akka.dispatch.forkjoin.ForkJoinPool",
      "akka.dispatch.MessageDispatcher",
      "akka.dispatch.Dispatcher",
      "akka.dispatch.Dispatcher$LazyExecutorServiceDelegate",
      "akka.actor.ActorSystemImpl$$anon$1",
      "akka.dispatch.ForkJoinExecutorConfigurator$AkkaForkJoinPool",
      "akka.dispatch.forkjoin.ForkJoinPool",
      "akka.dispatch.BalancingDispatcher",
      "akka.dispatch.ThreadPoolConfig$ThreadPoolExecutorServiceFactory$$anon$1",
      "akka.dispatch.PinnedDispatcher",
      "akka.dispatch.ExecutionContexts$sameThreadExecutionContext$",
      "akka.dispatch.ExecutionContexts$sameThreadExecutionContext$",
      "play.api.libs.streams.Execution$trampoline$"
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
      ExecutorInstrumentation.class.getName() + "$DatadogWrapper",
      ExecutorInstrumentation.class.getName() + "$CallableWrapper",
      ExecutorInstrumentation.class.getName() + "$RunnableWrapper"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        named("execute").and(takesArgument(0, Runnable.class)), WrapRunnableAdvice.class.getName());
    transformers.put(
        named("submit").and(takesArgument(0, Runnable.class)), WrapRunnableAdvice.class.getName());
    transformers.put(
        named("submit").and(takesArgument(0, Callable.class)), WrapCallableAdvice.class.getName());
    transformers.put(
        nameMatches("invoke(Any|All)$").and(takesArgument(0, Callable.class)),
        WrapCallableCollectionAdvice.class.getName());
    return transformers;
  }

  public static class WrapRunnableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static DatadogWrapper enterJobSubmit(
        @Advice.Argument(value = 0, readOnly = false) Runnable task) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (DatadogWrapper.shouldWrapTask(task)) {
        task = new RunnableWrapper(task, (TraceScope) scope);
        return (RunnableWrapper) task;
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Enter final DatadogWrapper wrapper, @Advice.Thrown final Throwable throwable) {
      DatadogWrapper.cleanUpOnMethodExit(wrapper, throwable);
    }
  }

  public static class WrapCallableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static DatadogWrapper enterJobSubmit(
        @Advice.Argument(value = 0, readOnly = false) Callable<?> task) {

      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (DatadogWrapper.shouldWrapTask(task)) {
        task = new CallableWrapper<>(task, (TraceScope) scope);
        return (CallableWrapper) task;
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Enter final DatadogWrapper wrapper, @Advice.Thrown final Throwable throwable) {
      DatadogWrapper.cleanUpOnMethodExit(wrapper, throwable);
    }
  }

  public static class WrapCallableCollectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Collection<?> wrapJob(
        @Advice.Argument(value = 0, readOnly = false) Collection<? extends Callable<?>> tasks) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (scope instanceof TraceScope
          && ((TraceScope) scope).isAsyncPropagating()
          && tasks != null
          && DatadogWrapper.isTopLevelCall()) {
        final Collection<Callable<?>> wrappedTasks = new ArrayList<>(tasks.size());
        for (final Callable<?> task : tasks) {
          if (task != null && !(task instanceof CallableWrapper)) {
            wrappedTasks.add(new CallableWrapper<>(task, (TraceScope) scope));
          }
        }
        tasks = wrappedTasks;
        return tasks;
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void checkCancel(
        @Advice.Enter final Collection<?> wrappedJobs, @Advice.Thrown final Throwable throwable) {
      if (null != wrappedJobs) {
        DatadogWrapper.resetNestedCalls();

        if (null != throwable) {
          for (final Object wrapper : wrappedJobs) {
            if (wrapper instanceof DatadogWrapper) {
              ((DatadogWrapper) wrapper).cancel();
            }
          }
        }
      }
    }
  }

  /** Marker interface for tasks which are wrapped to propagate the trace context. */
  @Slf4j
  public abstract static class DatadogWrapper {
    protected final TraceScope.Continuation continuation;

    public DatadogWrapper(final TraceScope scope) {
      continuation = scope.capture();
      log.debug("created continuation {} from scope {}", continuation, scope);
    }

    public void cancel() {
      if (null != continuation) {
        continuation.close();
        log.debug("canceled continuation {}", continuation);
      }
    }

    /**
     * Check if given call to executor is nested. We would like to ignore nested calls to execute to
     * avoid wrapping tasks twice. Note: this condition may lead to problems with executors that
     * 'fork' several tasks, but we do not have such executors at the moment. Note: this condition
     * is mutating and needs to be checked right before task is actually wrapped.
     *
     * @return true iff call is not nested
     */
    public static boolean isTopLevelCall() {
      return CallDepthThreadLocalMap.incrementCallDepth(ExecutorService.class) <= 0;
    }

    /** Reset nested calls to executor. */
    public static void resetNestedCalls() {
      CallDepthThreadLocalMap.reset(ExecutorService.class);
    }

    /**
     * @param task task object
     * @return true iff given task object should be wrapped
     */
    public static boolean shouldWrapTask(final Object task) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      return (scope instanceof TraceScope
          && ((TraceScope) scope).isAsyncPropagating()
          && task != null
          && !(task instanceof DatadogWrapper)
          && isTopLevelCall());
    }

    /**
     * Clean up after job submission method has exited
     *
     * @param wrapper task wrapper
     * @param throwable throwable that may have been thrown
     */
    public static void cleanUpOnMethodExit(
        final DatadogWrapper wrapper, final Throwable throwable) {
      if (null != wrapper) {
        resetNestedCalls();
        if (null != throwable) {
          wrapper.cancel();
        }
      }
    }
  }

  @Slf4j
  public static class RunnableWrapper extends DatadogWrapper implements Runnable {
    private final Runnable delegatee;

    public RunnableWrapper(final Runnable toWrap, final TraceScope scope) {
      super(scope);
      delegatee = toWrap;
    }

    @Override
    public void run() {
      final TraceScope context = continuation.activate();
      context.setAsyncPropagation(true);
      try {
        delegatee.run();
      } finally {
        context.close();
      }
    }
  }

  @Slf4j
  public static class CallableWrapper<T> extends DatadogWrapper implements Callable<T> {
    private final Callable<T> delegatee;

    public CallableWrapper(final Callable<T> toWrap, final TraceScope scope) {
      super(scope);
      delegatee = toWrap;
    }

    @Override
    public T call() throws Exception {
      final TraceScope context = continuation.activate();
      context.setAsyncPropagation(true);
      try {
        return delegatee.call();
      } finally {
        context.close();
      }
    }
  }

  /** Utils for pulling DatadogWrapper out of Future instances. */
  public static class ConcurrentUtils {
    private static final Map<Class<?>, Field> fieldCache = new ConcurrentHashMap<>();
    private static final String[] wrapperFields = {"runnable", "callable"};

    public static DatadogWrapper getDatadogWrapper(final Future<?> f) {
      final Field field;
      if (fieldCache.containsKey(f.getClass())) {
        field = fieldCache.get(f.getClass());
      } else {
        field = getWrapperField(f.getClass());
        fieldCache.put(f.getClass(), field);
      }

      if (field != null) {
        try {
          field.setAccessible(true);
          final Object o = field.get(f);
          if (o instanceof DatadogWrapper) {
            return (DatadogWrapper) o;
          }
        } catch (final IllegalArgumentException | IllegalAccessException e) {
        } finally {
          field.setAccessible(false);
        }
      }
      return null;
    }

    private static Field getWrapperField(Class<?> clazz) {
      Field field = null;
      while (field == null && clazz != null) {
        for (int i = 0; i < wrapperFields.length; ++i) {
          try {
            field = clazz.getDeclaredField(wrapperFields[i]);
            break;
          } catch (final Exception e) {
          }
        }
        clazz = clazz.getSuperclass();
      }
      return field;
    }
  }
}
