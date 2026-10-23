/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.classic;

import static java.util.Arrays.asList;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * Instrumentations for classic remoting, which pekko deprecated in favour of artery but still
 * supports.
 */
public final class ClassicInstrumentations {

  public static List<TypeInstrumentation> get() {
    return asList(
        new SendInstrumentation(),
        new EndpointWriterInstrumentation(),
        new PduCodecInstrumentation(),
        new MessageDispatcherInstrumentation());
  }

  private ClassicInstrumentations() {}
}
