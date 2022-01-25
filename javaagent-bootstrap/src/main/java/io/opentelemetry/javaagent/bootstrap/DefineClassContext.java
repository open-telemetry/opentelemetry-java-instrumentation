/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

/** Context for tracking whether helper classes were injected during defining class. */
public final class DefineClassContext {
  private static final ThreadLocal<DefineClassContext> contextThreadLocal = new ThreadLocal<>();

  private int counter;
  private boolean helpersInjected;

  private DefineClassContext() {}

  /**
   * Start defining class. Instrumentation inserts call to this method into ClassLoader.defineClass.
   */
  public static void enter() {
    DefineClassContext context = contextThreadLocal.get();
    if (context == null) {
      context = new DefineClassContext();
      contextThreadLocal.set(context);
    }
    context.counter++;
  }

  /**
   * Finish defining class. Instrumentation inserts call to this method into
   * ClassLoader.defineClass.
   */
  public static void exit() {
    exitAndGet();
  }

  /**
   * Finish defining class. Instrumentation inserts call to this method into
   * ClassLoader.defineClass.
   *
   * @return true if helper classes were injected
   */
  public static boolean exitAndGet() {
    DefineClassContext context = contextThreadLocal.get();
    context.counter--;
    if (context.counter == 0) {
      contextThreadLocal.remove();
    }
    return context.helpersInjected;
  }

  /** Called when helper classes are injected. */
  public static void helpersInjected() {
    DefineClassContext context = contextThreadLocal.get();
    if (context != null) {
      context.helpersInjected = true;
    }
  }
}
