package datadog.trace.common.serialization;

import java.io.IOException;
import java.math.BigInteger;
import org.msgpack.core.MessagePacker;

public class MsgpackFormatWriter extends FormatWriter<MessagePacker> {
  public static MsgpackFormatWriter MSGPACK_WRITER = new MsgpackFormatWriter();

  @Override
  public void writeKey(final String key, final MessagePacker destination) throws IOException {
    destination.packString(key);
  }

  @Override
  public void writeListHeader(final int size, final MessagePacker destination) throws IOException {
    destination.packArrayHeader(size);
  }

  @Override
  public void writeListFooter(final MessagePacker destination) throws IOException {}

  @Override
  public void writeMapHeader(final int size, final MessagePacker destination) throws IOException {
    destination.packMapHeader(size);
  }

  @Override
  public void writeMapFooter(final MessagePacker destination) throws IOException {}

  @Override
  public void writeString(final String key, final String value, final MessagePacker destination)
      throws IOException {
    destination.packString(key);
    if (value == null) {
      destination.packNil();
    } else {
      destination.packString(value);
    }
  }

  @Override
  public void writeShort(final String key, final short value, final MessagePacker destination)
      throws IOException {
    destination.packString(key);
    destination.packShort(value);
  }

  @Override
  public void writeByte(final String key, final byte value, final MessagePacker destination)
      throws IOException {
    destination.packString(key);
    destination.packByte(value);
  }

  @Override
  public void writeInt(final String key, final int value, final MessagePacker destination)
      throws IOException {
    destination.packString(key);
    destination.packInt(value);
  }

  @Override
  public void writeLong(final String key, final long value, final MessagePacker destination)
      throws IOException {
    destination.packString(key);
    destination.packLong(value);
  }

  @Override
  public void writeFloat(final String key, final float value, final MessagePacker destination)
      throws IOException {
    destination.packString(key);
    destination.packFloat(value);
  }

  @Override
  public void writeDouble(final String key, final double value, final MessagePacker destination)
      throws IOException {
    destination.packString(key);
    destination.packDouble(value);
  }

  @Override
  public void writeBigInteger(
      final String key, final BigInteger value, final MessagePacker destination)
      throws IOException {
    destination.packString(key);
    if (value == null) {
      destination.packNil();
    } else {
      destination.packBigInteger(value);
    }
  }
}
