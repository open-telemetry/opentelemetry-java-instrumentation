/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.concurrent;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;

/** Utils for concurrent instrumentations. */
public final class ExecutorInstrumentationUtils {
  private static final String AGENT_CLASSLOADER_NAME =
      "io.opentelemetry.javaagent.bootstrap.AgentClassLoader";

  private static final ClassValue<Boolean> INSTRUMENTED_RUNNABLE_CLASS =
      new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> taskClass) {
          // ForkJoinPool threads are initialized lazily and continue to handle tasks similar to an
          // event loop. They should not have context propagated to the base of the thread, tasks
          // themselves will have it through other means.
          if (taskClass.getName().equals("java.util.concurrent.ForkJoinWorkerThread")) {
            return false;
          }

          // ThreadPoolExecutor worker threads may be initialized lazily and manage interruption of
          // other threads. The actual tasks being run on those threads will propagate context but
          // we should not propagate onto this management thread.
          if (taskClass.getName().equals("java.util.concurrent.ThreadPoolExecutor$Worker")) {
            return false;
          }

          // TODO Workaround for
          // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/787
          if (taskClass
              .getName()
              .equals("org.apache.tomcat.util.net.NioEndpoint$SocketProcessor")) {
            return false;
          }

          // ScheduledRunnable is a wrapper around a Runnable and doesn't itself need context.
          if (taskClass.getName().equals("io.reactivex.internal.schedulers.ScheduledRunnable")) {
            return false;
          }

          // HttpConnection implements Runnable. When async request is completed HttpConnection
          // may be sent to process next request while context from previous request hasn't been
          // cleared yet.
          if (taskClass.getName().equals("org.eclipse.jetty.server.HttpConnection")) {
            return false;
          }

          // This is a Mailbox created by akka.dispatch.Dispatcher#createMailbox. We must not add
          // a context to it as context should only be carried by individual envelopes in the queue
          // of this mailbox.
          if (taskClass.getName().equals("akka.dispatch.Dispatcher$$anon$1")) {
            return false;
          }

          Class<?> enclosingClass = taskClass.getEnclosingClass();
          if (enclosingClass != null) {
            // Avoid context leak on jetty. Runnable submitted from SelectChannelEndPoint is used to
            // process a new request which should not have context from them current request.
            if (enclosingClass.getName().equals("org.eclipse.jetty.io.nio.SelectChannelEndPoint")) {
              return false;
            }

            // Don't instrument the executor's own runnables. These runnables may never return until
            // netty shuts down.
            if (enclosingClass
                .getName()
                .equals("io.netty.util.concurrent.SingleThreadEventExecutor")) {
              return false;
            }

            // OkHttp task runner is a lazily-initialized shared pool of continuosly running threads
            // similar to an event loop. The submitted tasks themselves should already be
            // instrumented to allow async propagation.
            if (enclosingClass.getName().equals("okhttp3.internal.concurrent.TaskRunner")) {
              return false;
            }

            // OkHttp connection pool lazily initializes a long running task to detect expired
            // connections and should not itself be instrumented.
            if (enclosingClass.getName().equals("com.squareup.okhttp.ConnectionPool")) {
              return false;
            }

            // Avoid instrumenting internal OrderedExecutor worker class
            if (enclosingClass
                .getName()
                .equals("org.hornetq.utils.OrderedExecutorFactory$OrderedExecutor")) {
              return false;
            }
          }

          // Don't trace runnables from libraries that are packaged inside the agent.
          // Although GlobalClassloaderIgnoresMatcher excludes these classes from instrumentation
          // their instances can still be passed to executors which we have instrumented so we need
          // to exclude them here too.
          ClassLoader taskClassLoader = taskClass.getClassLoader();
          if (taskClassLoader != null
              && AGENT_CLASSLOADER_NAME.equals(taskClassLoader.getClass().getName())) {
            return false;
          }

          if (taskClass.getName().startsWith("ratpack.exec.internal.")) {
            // Context is passed through Netty channels in Ratpack as executor instrumentation is
            // not suitable. As the context that would be propagated via executor would be
            // incorrect, skip the propagation. Not checking for concrete class names as this covers
            // anonymous classes from ratpack.exec.internal.DefaultExecution and
            // ratpack.exec.internal.DefaultExecController.
            return false;
          }

          return true;
        }
      };

  /**
   * Checks if given task should get state attached.
   *
   * @param task task object
   * @return true iff given task object should be wrapped
   */
  public static boolean shouldAttachStateToTask(Object task) {
    if (task == null) {
      return false;
    }

    if (Context.current() == Context.root()) {
      // not much point in propagating root context
      // plus it causes failures under otel.javaagent.testing.fail-on-context-leak=true
      return false;
    }

    return INSTRUMENTED_RUNNABLE_CLASS.get(task.getClass());
  }

  /**
   * Create task state given current scope.
   *
   * @param <T> task class type
   * @param contextStore context storage
   * @param task task instance
   * @param context current context
   * @return new state
   */
  public static <T> State setupState(ContextStore<T, State> contextStore, T task, Context context) {
    State state = contextStore.putIfAbsent(task, State.FACTORY);
    if (ContextPropagationDebug.isThreadPropagationDebuggerEnabled()) {
      context =
          ContextPropagationDebug.appendLocations(context, new Exception().getStackTrace(), task);
    }
    state.setParentContext(context);
    return state;
  }

  /**
   * Clean up after job submission method has exited.
   *
   * @param state task instrumentation state
   * @param throwable throwable that may have been thrown
   */
  public static void cleanUpOnMethodExit(State state, Throwable throwable) {
    if (null != state && null != throwable) {
      /*
      Note: this may potentially clear somebody else's parent span if we didn't set it
      up in setupState because it was already present before us. This should be safe but
      may lead to non-attributed async work in some very rare cases.
      Alternative is to not clear parent span here if we did not set it up in setupState
      but this may potentially lead to memory leaks if callers do not properly handle
      exceptions.
       */
      state.clearParentContext();
    }
  }

  private ExecutorInstrumentationUtils() {}
}
