/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery.RemoteMessageState.currentInboundEnvelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.pekko.remote.artery.InboundEnvelope;
import org.junit.jupiter.api.Test;

class RemoteMessageStateTest {

  @Test
  void restoresEnclosingEnvelopeAfterNestedDeserialization() {
    InboundEnvelope outer = mock(InboundEnvelope.class);
    InboundEnvelope inner = mock(InboundEnvelope.class);
    InboundEnvelope initial = currentInboundEnvelope().get();
    InboundEnvelope beforeOuter =
        RemoteInstrumentsSerializationInstrumentation.DeserializeAdvice.onEnter(outer);
    try {
      InboundEnvelope beforeInner =
          RemoteInstrumentsSerializationInstrumentation.DeserializeAdvice.onEnter(inner);
      try {
        assertThat(RemoteMessageState.envelopeToRead()).isSameAs(inner);
      } finally {
        RemoteInstrumentsSerializationInstrumentation.DeserializeAdvice.onExit(beforeInner);
      }
      assertThat(RemoteMessageState.envelopeToRead()).isSameAs(outer);
    } finally {
      RemoteInstrumentsSerializationInstrumentation.DeserializeAdvice.onExit(beforeOuter);
    }
    assertThat(currentInboundEnvelope().get()).isSameAs(initial);
  }
}
