package datadog.trace.instrumentation.java.concurrent;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public final class ExecutorInstrumentation extends Instrumenter.Configurable {
  public static final String EXEC_NAME = "java_concurrent";
  public static final HelperInjector EXEC_HELPER_INJECTOR =
      new HelperInjector(
          ExecutorInstrumentation.class.getName() + "$ConcurrentUtils",
          ExecutorInstrumentation.class.getName() + "$DatadogWrapper",
          ExecutorInstrumentation.class.getName() + "$CallableWrapper",
          ExecutorInstrumentation.class.getName() + "$RunnableWrapper");

  /**
   * Only apply executor instrumentation to whitelisted executors. In the future, this restriction
   * may be lifted to include all executors.
   */
  private static final Collection<String> WHITELISTED_EXECUTORS;

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
    WHITELISTED_EXECUTORS =
        Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(whitelist)));
  }

  public ExecutorInstrumentation() {
    super(EXEC_NAME);
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(not(isInterface()).and(hasSuperType(named(Executor.class.getName()))))
        .and(
            new ElementMatcher<TypeDescription>() {
              @Override
              public boolean matches(TypeDescription target) {
                final boolean whitelisted = WHITELISTED_EXECUTORS.contains(target.getName());
                if (!whitelisted) {
                  log.debug("Skipping executor instrumentation for {}", target.getName());
                }
                return whitelisted;
              }
            })
        .transform(EXEC_HELPER_INJECTOR)
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    named("execute").and(takesArgument(0, Runnable.class)),
                    WrapRunnableAdvice.class.getName()))
        .asDecorator()
        .type(not(isInterface()).and(hasSuperType(named(ExecutorService.class.getName()))))
        .transform(EXEC_HELPER_INJECTOR)
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    named("submit").and(takesArgument(0, Runnable.class)),
                    WrapRunnableAdvice.class.getName()))
        .transform(
            DDAdvice.create()
                .advice(
                    named("submit").and(takesArgument(0, Callable.class)),
                    WrapCallableAdvice.class.getName()))
        .transform(
            DDAdvice.create()
                .advice(
                    nameMatches("invoke(Any|All)$").and(takesArgument(0, Callable.class)),
                    WrapCallableCollectionAdvice.class.getName()))
        .asDecorator();
  }

  public static class WrapRunnableAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static DatadogWrapper wrapJob(
        @Advice.Argument(value = 0, readOnly = false) Runnable task) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (scope instanceof TraceScope
          && ((TraceScope) scope).isAsyncPropagating()
          && task != null
          && !(task instanceof DatadogWrapper)) {
        task = new RunnableWrapper(task, (TraceScope) scope);
        return (RunnableWrapper) task;
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void checkCancel(
        @Advice.Enter final DatadogWrapper wrapper, @Advice.Thrown final Throwable throwable) {
      if (null != wrapper && null != throwable) {
        wrapper.cancel();
      }
    }
  }

  public static class WrapCallableAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static DatadogWrapper wrapJob(
        @Advice.Argument(value = 0, readOnly = false) Callable task) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (scope instanceof TraceScope
          && ((TraceScope) scope).isAsyncPropagating()
          && task != null
          && !(task instanceof DatadogWrapper)) {
        task = new CallableWrapper(task, (TraceScope) scope);
        return (CallableWrapper) task;
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void checkCancel(
        @Advice.Enter final DatadogWrapper wrapper, @Advice.Thrown final Throwable throwable) {
      if (null != wrapper && null != throwable) {
        wrapper.cancel();
      }
    }
  }

  public static class WrapCallableCollectionAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Collection<?> wrapJob(
        @Advice.Argument(value = 0, readOnly = false) Collection<? extends Callable<?>> tasks) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (scope instanceof TraceScope && ((TraceScope) scope).isAsyncPropagating()) {
        Collection<Callable<?>> wrappedTasks = new ArrayList<>(tasks.size());
        for (Callable task : tasks) {
          if (task != null) {
            if (!(task instanceof CallableWrapper)) {
              task = new CallableWrapper(task, (TraceScope) scope);
            }
            wrappedTasks.add(task);
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
      if (null != wrappedJobs && null != throwable) {
        for (Object wrapper : wrappedJobs) {
          if (wrapper instanceof DatadogWrapper) {
            ((DatadogWrapper) wrapper).cancel();
          }
        }
      }
    }
  }

  /** Marker interface for tasks which are wrapped to propagate the trace context. */
  @Slf4j
  public abstract static class DatadogWrapper {
    protected final TraceScope.Continuation continuation;

    public DatadogWrapper(TraceScope scope) {
      continuation = scope.capture();
      log.debug("created continuation {} from scope {}", continuation, scope);
    }

    public void cancel() {
      if (null != continuation) {
        continuation.close();
        log.debug("canceled continuation {}", continuation);
      }
    }
  }

  @Slf4j
  public static class RunnableWrapper extends DatadogWrapper implements Runnable {
    private final Runnable delegatee;

    public RunnableWrapper(Runnable toWrap, TraceScope scope) {
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

    public CallableWrapper(Callable<T> toWrap, TraceScope scope) {
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
    private static Map<Class<?>, Field> fieldCache = new ConcurrentHashMap<>();
    private static String[] wrapperFields = {"runnable", "callable"};

    public static boolean safeToWrap(Future<?> f) {
      return null != getDatadogWrapper(f);
    }

    public static DatadogWrapper getDatadogWrapper(Future<?> f) {
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
          Object o = field.get(f);
          if (o instanceof DatadogWrapper) {
            return (DatadogWrapper) o;
          }
        } catch (IllegalArgumentException | IllegalAccessException e) {
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
          } catch (Exception e) {
          }
        }
        clazz = clazz.getSuperclass();
      }
      return field;
    }
  }
}
