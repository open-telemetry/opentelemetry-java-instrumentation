/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.internal;

import io.opentelemetry.instrumentation.api.config.Config;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InstrumentedTaskClasses {

  private static final Logger logger = LoggerFactory.getLogger(Config.class);

  private static final String AGENT_CLASSLOADER_NAME =
      "io.opentelemetry.javaagent.bootstrap.AgentClassLoader";

  private static final ClassValue<Boolean> INSTRUMENTED_TASK_CLASS =
      new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> taskClass) {
          // do not instrument ignored task classes
          if (ignoredTaskClassesPredicate.test(taskClass.getName())) {
            return false;
          }
          // Don't trace runnables from libraries that are packaged inside the agent.
          // Although GlobalIgnoredTypesConfigurer excludes these classes from instrumentation
          // their instances can still be passed to executors which we have instrumented so we need
          // to exclude them here too.
          ClassLoader taskClassLoader = taskClass.getClassLoader();
          if (taskClassLoader != null
              && AGENT_CLASSLOADER_NAME.equals(taskClassLoader.getClass().getName())) {
            return false;
          }
          return true;
        }
      };

  private static volatile Predicate<String> ignoredTaskClassesPredicate;

  /**
   * Sets the configured ignored tasks predicate (that uses a trie to find the common prefix). This
   * method is called internally from the agent classloader.
   */
  public static void setIgnoredTaskClassesPredicate(Predicate<String> ignoredTasksTriePredicate) {
    if (InstrumentedTaskClasses.ignoredTaskClassesPredicate != null) {
      logger.warn("Ignored task classes were already set earlier; returning.");
      return;
    }
    InstrumentedTaskClasses.ignoredTaskClassesPredicate = ignoredTasksTriePredicate;
  }

  /**
   * Returns {@code true} when passes {@code taskClass} is allowed to be instrumented; i.e. the
   * instrumentation may attach context to this task.
   */
  public static boolean canInstrumentTaskClass(Class<?> taskClass) {
    return INSTRUMENTED_TASK_CLASS.get(taskClass);
  }

  private InstrumentedTaskClasses() {}
}
