/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import static org.awaitility.Awaitility.await;

import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class AwaitUtil {
  private AwaitUtil() {}

  public static void awaitUntilAsserted(Runnable runnable) {
    awaitUntilAsserted(runnable, await());
  }

  public static void awaitUntilAsserted(Runnable runnable, ConditionFactory conditionFactory) {
    try {
      conditionFactory.untilAsserted(runnable::run);
    } catch (Throwable t) {
      // awaitility is doing a jmx call that is not implemented in GraalVM:
      // call:
      // https://github.com/awaitility/awaitility/blob/fbe16add874b4260dd240108304d5c0be84eabc8/awaitility/src/main/java/org/awaitility/core/ConditionAwaiter.java#L157
      // see https://github.com/oracle/graal/issues/6101 (spring boot graal native image)
      if (t.getClass().getName().equals("com.oracle.svm.core.jdk.UnsupportedFeatureError")
          || t instanceof ConditionTimeoutException) {
        // Don't throw this failure since the stack is the awaitility thread, causing confusion.
        // Instead, just assert one more time on the test thread, which will fail with a better
        // stack trace - that is on the same thread as the test.
        // TODO: There is probably a better way to do this.
        runnable.run();
      } else {
        throw t;
      }
    }
  }
}
