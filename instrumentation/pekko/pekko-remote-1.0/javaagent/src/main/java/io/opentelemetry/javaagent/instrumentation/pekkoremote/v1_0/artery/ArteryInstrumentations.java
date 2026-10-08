/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery;

import static java.util.Arrays.asList;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/** Instrumentations for artery, the remoting implementation that pekko uses by default. */
public final class ArteryInstrumentations {

  public static List<TypeInstrumentation> get() {
    return asList(
        new RemoteInstrumentsInstrumentation(),
        new RemoteInstrumentsSerializationInstrumentation(),
        new OutboundEnvelopeInstrumentation(),
        new InboundEnvelopeInstrumentation(),
        new MessageDispatcherInstrumentation());
  }

  private ArteryInstrumentations() {}
}
