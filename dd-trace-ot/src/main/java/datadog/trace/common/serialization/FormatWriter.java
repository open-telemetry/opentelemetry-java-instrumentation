package datadog.trace.common.serialization;

import datadog.opentracing.DDSpan;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public abstract class FormatWriter<DEST> {
  public abstract void writeKey(String key, DEST destination) throws IOException;

  public abstract void writeListHeader(int size, DEST destination) throws IOException;

  public abstract void writeListFooter(DEST destination) throws IOException;

  public abstract void writeMapHeader(int size, DEST destination) throws IOException;

  public abstract void writeMapFooter(DEST destination) throws IOException;

  public abstract void writeString(String key, String value, DEST destination) throws IOException;

  public abstract void writeShort(String key, short value, DEST destination) throws IOException;

  public abstract void writeByte(String key, byte value, DEST destination) throws IOException;

  public abstract void writeInt(String key, int value, DEST destination) throws IOException;

  public abstract void writeLong(String key, long value, DEST destination) throws IOException;

  public abstract void writeFloat(String key, float value, DEST destination) throws IOException;

  public abstract void writeDouble(String key, double value, DEST destination) throws IOException;

  public abstract void writeBigInteger(String key, BigInteger value, DEST destination)
      throws IOException;

  public void writeNumber(final String key, final Number value, final DEST destination)
      throws IOException {
    if (value instanceof Double) {
      writeDouble(key, value.doubleValue(), destination);
    } else if (value instanceof Long) {
      writeLong(key, value.longValue(), destination);
    } else if (value instanceof Integer) {
      writeInt(key, value.intValue(), destination);
    } else if (value instanceof Float) {
      writeFloat(key, value.floatValue(), destination);
    } else if (value instanceof Byte) {
      writeByte(key, value.byteValue(), destination);
    } else if (value instanceof Short) {
      writeShort(key, value.shortValue(), destination);
    }
  }

  public void writeNumberMap(
      final String key, final Map<String, Number> value, final DEST destination)
      throws IOException {
    writeKey(key, destination);
    writeMapHeader(value.size(), destination);
    for (final Map.Entry<String, Number> entry : value.entrySet()) {
      writeNumber(entry.getKey(), entry.getValue(), destination);
    }
    writeMapFooter(destination);
  }

  public void writeStringMap(
      final String key, final Map<String, String> value, final DEST destination)
      throws IOException {
    writeKey(key, destination);
    writeMapHeader(value.size(), destination);
    for (final Map.Entry<String, String> entry : value.entrySet()) {
      writeString(entry.getKey(), entry.getValue(), destination);
    }
    writeMapFooter(destination);
  }

  public void writeTrace(final List<DDSpan> trace, final DEST destination) throws IOException {
    writeListHeader(trace.size(), destination);
    for (final DDSpan span : trace) {
      writeDDSpan(span, destination);
    }
    writeListFooter(destination);
  }

  public void writeDDSpan(final DDSpan span, final DEST destination) throws IOException {
    // Some of the tests rely on the specific ordering here.
    writeMapHeader(12, destination); // must match count below.
    /* 1  */ writeString("service", span.getServiceName(), destination);
    /* 2  */ writeString("name", span.getOperationName(), destination);
    /* 3  */ writeString("resource", span.getResourceName(), destination);
    /* 4  */ writeBigInteger("trace_id", span.getTraceId(), destination);
    /* 5  */ writeBigInteger("span_id", span.getSpanId(), destination);
    /* 6  */ writeBigInteger("parent_id", span.getParentId(), destination);
    /* 7  */ writeLong("start", span.getStartTime(), destination);
    /* 8  */ writeLong("duration", span.getDurationNano(), destination);
    /* 9  */ writeString("type", span.getType(), destination);
    /* 10 */ writeInt("error", span.getError(), destination);
    /* 11 */ writeNumberMap("metrics", span.getMetrics(), destination);
    /* 12 */ writeStringMap("meta", span.getMeta(), destination);
    writeMapFooter(destination);
  }
}
