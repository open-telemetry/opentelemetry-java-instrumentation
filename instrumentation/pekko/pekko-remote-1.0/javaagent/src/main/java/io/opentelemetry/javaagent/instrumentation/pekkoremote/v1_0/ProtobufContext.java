/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import io.opentelemetry.context.Context;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import javax.annotation.Nullable;

/**
 * Carries the context in a protobuf field that is appended to the message of classic remoting.
 *
 * <p>Appending a field to a serialized protobuf message produces a valid message, a parser that
 * does not know the field keeps it as an unknown field, so a node that runs without the agent
 * ignores what is added here.
 */
public final class ProtobufContext {

  // pekko uses 1 and 2 in AckAndEnvelopeContainer, kamon uses 15 within the envelope
  private static final int FIELD_NUMBER = 1000;
  private static final int WIRE_TYPE_VARINT = 0;
  private static final int WIRE_TYPE_FIXED64 = 1;
  private static final int WIRE_TYPE_LENGTH_DELIMITED = 2;
  private static final int WIRE_TYPE_FIXED32 = 5;
  private static final int MAX_SIZE = 4 * 1024;
  // a varint holds up to 64 bits, seven of them per byte
  private static final int MAX_VAR_INT_BYTES = 10;

  private static final byte[] EMPTY = new byte[0];

  /** Returns the field that carries the given context, empty when there is nothing to write. */
  public static byte[] encode(Context context) {
    ByteBuffer buffer = ByteBuffer.allocate(MAX_SIZE);
    ContextMetadata.write(context, buffer);
    int length = buffer.position();
    if (length == 0) {
      return EMPTY;
    }

    ByteBuffer field = ByteBuffer.allocate(length + 10);
    writeVarInt(field, (FIELD_NUMBER << 3) | WIRE_TYPE_LENGTH_DELIMITED);
    writeVarInt(field, length);
    buffer.flip();
    field.put(buffer);

    byte[] result = new byte[field.position()];
    field.flip();
    field.get(result);
    return result;
  }

  /**
   * Reads the context from the message, null when the message does not carry one.
   *
   * <p>The bytes are whatever arrived, and this runs from advice, so anything that does not parse
   * is reported as no context rather than thrown at pekko.
   */
  @Nullable
  public static Context decode(byte[] message) {
    try {
      return read(ByteBuffer.wrap(message));
    } catch (BufferUnderflowException | IllegalArgumentException | IllegalStateException ignored) {
      return null;
    }
  }

  @Nullable
  private static Context read(ByteBuffer buffer) {
    while (buffer.hasRemaining()) {
      int tag = readVarInt(buffer);
      int fieldNumber = tag >>> 3;
      int wireType = tag & 7;
      if (fieldNumber == FIELD_NUMBER && wireType == WIRE_TYPE_LENGTH_DELIMITED) {
        int length = readVarInt(buffer);
        buffer.limit(buffer.position() + length);
        return ContextMetadata.read(buffer);
      }
      if (!skipField(buffer, wireType)) {
        return null;
      }
    }
    return null;
  }

  private static boolean skipField(ByteBuffer buffer, int wireType) {
    switch (wireType) {
      case WIRE_TYPE_VARINT:
        readVarInt(buffer);
        return true;
      case WIRE_TYPE_FIXED64:
        buffer.position(buffer.position() + 8);
        return true;
      case WIRE_TYPE_LENGTH_DELIMITED:
        // read the length before asking for the position, the buffer advances while it is read
        int length = readVarInt(buffer);
        buffer.position(buffer.position() + length);
        return true;
      case WIRE_TYPE_FIXED32:
        buffer.position(buffer.position() + 4);
        return true;
      default:
        // groups are deprecated and pekko does not use them, stop looking
        return false;
    }
  }

  private static void writeVarInt(ByteBuffer buffer, int value) {
    while ((value & ~0x7F) != 0) {
      buffer.put((byte) ((value & 0x7F) | 0x80));
      value >>>= 7;
    }
    buffer.put((byte) value);
  }

  /**
   * Reads a varint of any width, keeping the low 32 bits of it. A value that is wider than that is
   * one this only has to step over, and protobuf itself truncates a wide varint read into an int.
   */
  private static int readVarInt(ByteBuffer buffer) {
    int result = 0;
    for (int index = 0; index < MAX_VAR_INT_BYTES; index++) {
      byte current = buffer.get();
      if (index < 5) {
        result |= (current & 0x7F) << (index * 7);
      }
      if ((current & 0x80) == 0) {
        return result;
      }
    }
    throw new IllegalStateException("malformed varint");
  }

  private ProtobufContext() {}
}
