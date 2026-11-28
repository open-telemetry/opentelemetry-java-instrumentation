/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery;

import io.opentelemetry.context.Context;
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

  private static final ThreadLocal<Context> OUTBOUND_CONTEXT = new ThreadLocal<>();
  private static final ThreadLocal<InboundEnvelope> INBOUND_ENVELOPE = new ThreadLocal<>();

  public static void startWrite(@Nullable Context context) {
    if (context != null) {
      OUTBOUND_CONTEXT.set(context);
    }
  }

  public static void endWrite() {
    OUTBOUND_CONTEXT.remove();
  }

  @Nullable
  static Context contextToWrite() {
    return OUTBOUND_CONTEXT.get();
  }

  public static void startRead(@Nullable InboundEnvelope envelope) {
    if (envelope != null) {
      INBOUND_ENVELOPE.set(envelope);
    }
  }

  public static void endRead() {
    INBOUND_ENVELOPE.remove();
  }

  @Nullable
  static InboundEnvelope envelopeToRead() {
    return INBOUND_ENVELOPE.get();
  }

  private RemoteMessageState() {}
}
