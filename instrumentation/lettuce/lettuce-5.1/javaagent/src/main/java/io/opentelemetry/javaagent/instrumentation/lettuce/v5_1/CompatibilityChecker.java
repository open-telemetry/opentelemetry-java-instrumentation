/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracing;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CompatibilityChecker {

  private CompatibilityChecker() {}

  private static Boolean isCompatible;
  private static final Lock lock = new ReentrantLock();

  // related to https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10997
  // if users are using incompatible versions of reactor-core and lettuce
  // then just disable the instrumentation
  public static boolean checkCompatible() {
    lock.lock();
    try {
      if (isCompatible != null) {
        return isCompatible;
      }
      Tracing.getContext();
      isCompatible = true;
    } catch (Throwable t) {
      isCompatible = false;
    } finally {
      lock.unlock();
    }
    return isCompatible;
  }
}
