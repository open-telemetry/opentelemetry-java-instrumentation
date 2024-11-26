/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.internal;

import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InstrumentationUtil {

  public static Instrumentation addInstrumentation(
      Instrumentation instrumentation, Instrumentation ourInstrumentation) {
    if (instrumentation == null) {
      return ourInstrumentation;
    }
    if (instrumentation.getClass() == ourInstrumentation.getClass()) {
      return instrumentation;
    }
    List<Instrumentation> instrumentationList = new ArrayList<>();
    if (instrumentation instanceof ChainedInstrumentation) {
      instrumentationList.addAll(((ChainedInstrumentation) instrumentation).getInstrumentations());
    } else {
      instrumentationList.add(instrumentation);
    }
    boolean containsOurInstrumentation =
        instrumentationList.stream().anyMatch(ourInstrumentation.getClass()::isInstance);
    if (!containsOurInstrumentation) {
      instrumentationList.add(0, ourInstrumentation);
    }
    return new ChainedInstrumentation(instrumentationList);
  }

  private InstrumentationUtil() {}
}
