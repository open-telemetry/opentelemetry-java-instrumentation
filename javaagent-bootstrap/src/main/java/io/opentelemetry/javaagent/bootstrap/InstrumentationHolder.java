/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.lang.instrument.Instrumentation;
import javax.annotation.Nullable;

/** This class serves as an "everywhere accessible" source of {@link Instrumentation} instance. */
public class InstrumentationHolder {

  @Nullable private static volatile Instrumentation instrumentation;

  @Nullable
  public static Instrumentation getInstrumentation() {
    return instrumentation;
  }

  public static void setInstrumentation(Instrumentation instrumentation) {
    InstrumentationHolder.instrumentation = instrumentation;
  }
}
