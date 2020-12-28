/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.spockframework.runtime.Sputnik;

/**
 * Customized spock test runner that runs tests on multiple app server versions based on {@link
 * AppServer} annotations. This runner selects first server based on {@link AppServer} annotation
 * calls setupSpec, all test method and cleanupSpec, selects next {@link AppServer} and calls the
 * same methods. This process is repeated until tests have run for all {@link AppServer}
 * annotations. Tests should start server in setupSpec and stop it in cleanupSpec.
 */
public class AppServerTestRunner extends Sputnik {
  private static final Map<Class<?>, AppServer> runningAppServer =
      Collections.synchronizedMap(new HashMap<>());
  private static final ThreadLocal<Class<?>> currentTestClass = new ThreadLocal<>();
  private final Class<?> testClass;
  private final AppServer[] appServers;

  public AppServerTestRunner(Class<?> clazz) throws InitializationError {
    super(clazz);
    testClass = clazz;
    appServers = clazz.getAnnotationsByType(AppServer.class);
    if (appServers.length == 0) {
      throw new IllegalStateException("Add AppServer or AppServers annotation to test class");
    }
  }

  @Override
  public void run(RunNotifier notifier) {
    // run tests for all app servers
    try {
      for (AppServer a : appServers) {
        runningAppServer.put(testClass, a);
        super.run(notifier);
      }
    } finally {
      runningAppServer.remove(testClass);
    }
  }

  @Override
  public Description getDescription() {
    //
    currentTestClass.set(testClass);
    try {
      return super.getDescription();
    } finally {
      currentTestClass.remove();
    }
  }

  // expose currently running app server
  // used to get current server and jvm version inside the test class
  public static AppServer currentAppServer(Class<?> testClass) {
    AppServer a = runningAppServer.get(testClass);
    if (a == null) {
      throw new IllegalStateException("Test not running for " + testClass);
    }
    return a;
  }

  // expose current test class
  // used for ignoring tests defined in base class that are expected to fail
  // on currently running server
  public static Class<?> currentTestClass() {
    Class<?> c = currentTestClass.get();
    if (c == null) {
      throw new IllegalStateException("Current test class is not set");
    }
    return c;
  }
}
