/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import javax.annotation.Nullable;
import org.apache.pekko.remote.artery.InboundEnvelope;

/**
 * Connects the envelope that pekko is (de)serializing with the {@code RemoteInstrument} that reads
 * and writes the context.
 *
 * <p>{@code RemoteInstrument} is called with the message, not with the envelope, and the message
 * can not be used to look up the envelope, deserializing a scala {@code case object} returns a
 * shared instance. Pekko calls the instrument from the same thread that (de)serializes the
 * envelope, so the envelope can be handed over in a thread local.
 */
public final class RemoteMessageState {

  private static final ScopedThreadValue<Context> outboundContext = new ScopedThreadValue<>();
  private static final ScopedThreadValue<InboundEnvelope> inboundEnvelope =
      new ScopedThreadValue<>();

  public static ScopedThreadValue<Context> outboundContext() {
    return outboundContext;
  }

  @Nullable
  static Context contextToWrite() {
    return outboundContext.get();
  }

  public static ScopedThreadValue<InboundEnvelope> inboundEnvelope() {
    return inboundEnvelope;
  }

  @Nullable
  static InboundEnvelope envelopeToRead() {
    return inboundEnvelope.get();
  }

  private RemoteMessageState() {}
}
