/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.pekkoremote.v1_0.internal;

import static java.nio.charset.StandardCharsets.US_ASCII;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import java.nio.ByteBuffer;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSelectionMessage;
import org.apache.pekko.remote.HeartbeatMessage;
import org.apache.pekko.remote.artery.ArteryMessage;
import org.apache.pekko.remote.artery.RemoteInstrument;
import org.apache.pekko.util.Unsafe;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class OtelRemoteInstrument extends RemoteInstrument {

  private final byte identifier;

  private String currentlyReadMetadata = null;

  public OtelRemoteInstrument(byte identifier) {
    this.identifier = identifier;
  }

  @Override
  public byte identifier() {
    return identifier;
  }

  @Override
  public void remoteWriteMetadata(
      ActorRef recipient, Object message, ActorRef sender, ByteBuffer buffer) {
    if (nonPekkoInternalMessage(message)) {

      StringBuilder carrier = new StringBuilder();
      W3CTraceContextPropagator.getInstance()
          .inject(Context.current(), carrier, StringBuilderTextMapSetter.INSTANCE);

      int length = carrier.length();
      if (length <= 65534) {

        byte[] bytes = new byte[length];
        Unsafe.copyUSAsciiStrToBytes(carrier.toString(), bytes);

        buffer.putShort((short) length);
        buffer.put(bytes);

      } else {
        buffer.putShort((short) 0);
      }

    } else {
      buffer.putShort((short) 0);
    }
  }

  @Override
  public void remoteMessageSent(
      ActorRef recipient, Object message, ActorRef sender, int size, long time) {}

  @Override
  public void remoteReadMetadata(
      ActorRef recipient, Object message, ActorRef sender, ByteBuffer buffer) {
    int length = buffer.getShort();

    if (length > 0) {

      byte[] bytes = new byte[length];
      buffer.get(bytes);

      String metadata = new String(bytes, US_ASCII);

      this.currentlyReadMetadata = metadata;
    } else {
      this.currentlyReadMetadata = null;
    }
  }

  @Override
  public void remoteMessageReceived(
      ActorRef recipient, Object message, ActorRef sender, int size, long time) {}

  public String getCurrentlyReadMetadata() {
    return currentlyReadMetadata;
  }

  public void clearCurrentlyReadMetadata() {
    currentlyReadMetadata = null;
  }

  private static boolean nonPekkoInternalMessage(Object message) {
    if (message instanceof ActorSelectionMessage) {
      message = ((ActorSelectionMessage) message).message();
    }
    return !(message instanceof HeartbeatMessage) && !(message instanceof ArteryMessage);
  }
}
