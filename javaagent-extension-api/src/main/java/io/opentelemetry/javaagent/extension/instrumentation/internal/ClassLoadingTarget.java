/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time. <br>
 */
public enum ClassLoadingTarget {
  /**
   * Class or package will be injected into the instrumented classloader, as if the class was
   * present in the application classpath. When referenced directly from instrumentation advice or
   * helper classes, it should never be loaded in the instrumentation module or agent classloader.
   */
  INSTRUMENTATION_TARGET,
  /**
   * Class or package will be injected into an isolated instrumentation module classloader when
   * using InvokeDynamic instrumentation. This is the default for most instrumentation classes as
   * they need to be isolated from the instrumented application. <br>
   * When using inlined instrumentation, this is equivalent to {@link #INSTRUMENTATION_TARGET}.
   */
  INSTRUMENTATION_ISOLATED,
  /**
   * Class or package will be injected into a shared instrumentation module classloader when using
   * InvokeDynamic instrumentation. This should be used for shared libraries classes and classes
   * that are used across multiple instrumentation modules. <br>
   * When using inlined instrumentation, this is equivalent to {@link #INSTRUMENTATION_TARGET}.
   */
  INSTRUMENTATION_SHARED
}
