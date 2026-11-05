/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v1_2;

import java.lang.reflect.Field;
import org.apache.log4j.helpers.Loader;

final class Log4jMdcTestHelper {

  static void enableMdc() {
    // Log4j 1.2 mistakes undotted Java versions for Java 1.1, which has no ThreadLocal.
    try {
      Field java1 = Loader.class.getDeclaredField("java1");
      java1.setAccessible(true);
      java1.set(null, false);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private Log4jMdcTestHelper() {}
}
