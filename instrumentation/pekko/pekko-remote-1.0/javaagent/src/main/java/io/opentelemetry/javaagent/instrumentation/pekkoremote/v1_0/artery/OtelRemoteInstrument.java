/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery.VirtualFields.INBOUND_ENVELOPE_CONTEXT;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.ContextMetadata;
import java.nio.ByteBuffer;
import javax.annotation.Nullable;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.remote.artery.InboundEnvelope;
import org.apache.pekko.remote.artery.RemoteInstrument;

/**
 * Attaches the context to the metadata section of a remote message. Pekko keeps the instruments of
 * both sides sorted by identifier and skips the metadata of an identifier that the receiving node
 * does not know, so a node that runs without the agent ignores what this instrument writes.
 */
public class OtelRemoteInstrument extends RemoteInstrument {

  // must be >= 1 and < 32, values from 1 to 7 are reserved for pekko, 0 is used by Lightbend
  // Telemetry and 8 by Kamon
  private static final byte IDENTIFIER = 9;

  @Override
  public byte identifier() {
    return IDENTIFIER;
  }

  @Override
  public void remoteWriteMetadata(
      @Nullable ActorRef recipient, Object message, @Nullable ActorRef sender, ByteBuffer buffer) {
    Context context = RemoteMessageState.contextToWrite();
    if (context != null) {
      ContextMetadata.write(context, buffer);
    }
  }

  @Override
  public void remoteReadMetadata(
      @Nullable ActorRef recipient, Object message, @Nullable ActorRef sender, ByteBuffer buffer) {
    InboundEnvelope envelope = RemoteMessageState.envelopeToRead();
    if (envelope == null) {
      return;
    }
    Context context = ContextMetadata.read(buffer);
    if (context != null) {
      INBOUND_ENVELOPE_CONTEXT.set(envelope, context);
    }
  }

  @Override
  public void remoteMessageSent(
      @Nullable ActorRef recipient,
      Object message,
      @Nullable ActorRef sender,
      int size,
      long time) {}

  @Override
  public void remoteMessageReceived(
      @Nullable ActorRef recipient,
      Object message,
      @Nullable ActorRef sender,
      int size,
      long time) {}
}
