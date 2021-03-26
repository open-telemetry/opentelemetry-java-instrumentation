/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.concurrent;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.context.ContextPropagationDebug;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Utils for concurrent instrumentations. */
public class ExecutorInstrumentationUtils {

  private static final ClassValue<Boolean> NOT_INSTRUMENTED_RUNNABLE_ENCLOSING_CLASS =
      new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> enclosingClass) {
          // Avoid context leak on jetty. Runnable submitted from SelectChannelEndPoint is used to
          // process a new request which should not have context from them current request.
          if (enclosingClass.getName().equals("org.eclipse.jetty.io.nio.SelectChannelEndPoint")) {
            return true;
          }

          // Don't instrument the executor's own runnables. These runnables may never return until
          // netty shuts down.
          if (enclosingClass
              .getName()
              .equals("io.netty.util.concurrent.SingleThreadEventExecutor")) {
            return true;
          }

          // OkHttp task runner is a lazily-initialized shared pool of continuosly running threads
          // similar to an event loop. The submitted tasks themselves should already be instrumented
          // to
          // allow async propagation.
          if (enclosingClass.getName().equals("okhttp3.internal.concurrent.TaskRunner")) {
            return true;
          }

          // OkHttp connection pool lazily initializes a long running task to detect expired
          // connections
          // and should not itself be instrumented.
          if (enclosingClass.getName().equals("com.squareup.okhttp.ConnectionPool")) {
            return true;
          }

          // The Grizzly HTTP handler seems to be run within the context of a request which can be
          // suspended causing a different request to be executed. Assuming our tests are complete,
          // it does not seem to be required to propagate context into this task.
          //
          // https://github.com/eclipse-ee4j/grizzly/blob/a2ce7775658e11fbccbb9acd32e2daf2b0799f45/modules/http-server/src/main/java/org/glassfish/grizzly/http/server/HttpHandler.java#L178
          if (enclosingClass.getName().equals("org.glassfish.grizzly.http.server.HttpHandler")) {
            return true;
          }

          return false;
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

    Class<?> taskClass = task.getClass();
    Class<?> enclosingClass = taskClass.getEnclosingClass();

    if (Context.current() == Context.root()) {
      // not much point in propagating root context
      // plus it causes failures under otel.javaagent.testing.fail-on-context-leak=true
      return false;
    }

    // ForkJoinPool threads are initialized lazily and continue to handle tasks similar to an event
    // loop. They should not have context propagated to the base of the thread, tasks themselves
    // will have it through other means.
    if (taskClass.getName().equals("java.util.concurrent.ForkJoinWorkerThread")) {
      return false;
    }

    // TODO Workaround for
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/787
    if (taskClass.getName().equals("org.apache.tomcat.util.net.NioEndpoint$SocketProcessor")) {
      return false;
    }

    // Long running task which decorates other tasks. We should probably be using ExecutionContext
    // for propagation in Scala but in the meantime this is not needed along with other concurrent
    // instrumentation and causes context leaks.
    if (taskClass.getName().equals("akka.util.SerializedSuspendableExecutionContext")) {
      return false;
    }

    // Wrapper of tasks for dispatch - the wrapped task should have context already and this doesn't
    // need it. It's not obvious why this simple wrapper (not long running task) could leak context.
    // One hypothesis is Scala can rewrite any code to introduce suspend / resume, including the
    // standard Runnable.run. If so, then this check should be generalized to exclude all Scala
    // classes because it is never safe to make context active in a code that has a chance to
    // suspend.
    if (taskClass.getName().equals("akka.dispatch.TaskInvocation")) {
      return false;
    }

    // See above.
    if (taskClass.getName().equals("scala.concurrent.impl.Future$PromiseCompletingRunnable")) {
      return false;
    }

    if (enclosingClass != null && NOT_INSTRUMENTED_RUNNABLE_ENCLOSING_CLASS.get(enclosingClass)) {
      return false;
    }

    return true;
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
      List<StackTraceElement[]> locations = ContextPropagationDebug.getLocations(context);
      if (locations == null) {
        locations = new CopyOnWriteArrayList<>();
        context = ContextPropagationDebug.withLocations(locations, context);
      }
      locations.add(0, new Exception().getStackTrace());
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
}
