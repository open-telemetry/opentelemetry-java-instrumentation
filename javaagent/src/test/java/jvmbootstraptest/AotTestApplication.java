/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

import io.opentelemetry.javaagent.bootstrap.field.VirtualFieldInstalledMarker;
import io.opentracing.contrib.dropwizard.Trace;

public class AotTestApplication implements Runnable {

  public static void main(String[] args) throws ReflectiveOperationException {
    new AotTestApplication().run();

    if (Boolean.getBoolean("otel.aot.test.production")) {
      Class.forName("jvmbootstraptest.AotTestPostPremainRunnable")
          .asSubclass(Runnable.class)
          .getConstructor()
          .newInstance()
          .run();
    }
  }

  @Override
  public void run() {
    if (hasInjectedField(getClass())) {
      throw new IllegalStateException("Field injected into AOT-cached class");
    }
    System.out.println("AOT_CACHED_CLASS_MAP_BACKED");
    System.out.println(traced());
  }

  @Trace
  private static String traced() {
    return "AOT_INSTRUMENTATION_MARKER";
  }

  static boolean hasInjectedField(Class<?> type) {
    // Class.getInterfaces() hides this internal marker when reflection instrumentation is active.
    return VirtualFieldInstalledMarker.class.isAssignableFrom(type);
  }

  private AotTestApplication() {}
}
