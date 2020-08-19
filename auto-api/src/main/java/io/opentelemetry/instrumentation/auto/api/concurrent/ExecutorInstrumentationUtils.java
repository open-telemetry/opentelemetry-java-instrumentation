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

package io.opentelemetry.instrumentation.auto.api.concurrent;

import static io.opentelemetry.instrumentation.auto.api.concurrent.AdviceUtils.TRACER;

import io.grpc.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.WeakMap;
import io.opentelemetry.trace.Span;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utils for concurrent instrumentations. */
public class ExecutorInstrumentationUtils {

  private static final Logger log = LoggerFactory.getLogger(ExecutorInstrumentationUtils.class);

  private static final WeakMap<Executor, Boolean> EXECUTORS_DISABLED_FOR_WRAPPED_TASKS =
      WeakMap.Provider.newWeakMap();

  /**
   * Checks if given task should get state attached.
   *
   * @param task task object
   * @param executor executor this task was scheduled on
   * @return true iff given task object should be wrapped
   */
  public static boolean shouldAttachStateToTask(Object task, Executor executor) {
    if (task == null) {
      return false;
    }

    Span span = TRACER.getCurrentSpan();
    Class<?> taskClass = task.getClass();
    Class<?> enclosingClass = taskClass.getEnclosingClass();

    return span.getContext().isValid()
        && !ExecutorInstrumentationUtils.isExecutorDisabledForThisTask(executor, task)
        // TODO Workaround for
        // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/787
        && !taskClass.getName().equals("org.apache.tomcat.util.net.NioEndpoint$SocketProcessor")
        // Don't instrument the executor's own runnables.  These runnables may never return until
        // netty shuts down.  Any created continuations will be open until that time preventing
        // traces from being reported
        && (enclosingClass == null
            || !enclosingClass
                .getName()
                .equals("io.netty.util.concurrent.SingleThreadEventExecutor"));
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
    if (Config.THREAD_PROPAGATION_DEBUGGER) {
      List<StackTraceElement[]> location = Config.THREAD_PROPAGATION_LOCATIONS.get(context);
      if (location == null) {
        location = new CopyOnWriteArrayList<>();
        context = context.withValue(Config.THREAD_PROPAGATION_LOCATIONS, location);
      }
      location.add(0, new Exception().getStackTrace());
    }
    state.setParentSpan(context);
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

  public static void disableExecutorForWrappedTasks(Executor executor) {
    log.debug("Disabling Executor tracing for wrapped tasks for instance {}", executor);
    EXECUTORS_DISABLED_FOR_WRAPPED_TASKS.put(executor, true);
  }

  /**
   * Check if Executor can accept given task.
   *
   * <p>Disabled executors cannot accept wrapped tasks, non wrapped tasks (i.e. tasks with injected
   * fields) should still work fine.
   */
  public static boolean isExecutorDisabledForThisTask(Executor executor, Object task) {
    return (task instanceof RunnableWrapper || task instanceof CallableWrapper)
        && EXECUTORS_DISABLED_FOR_WRAPPED_TASKS.containsKey(executor);
  }
}
