package datadog.trace.common.serialization;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import datadog.opentracing.DDSpan;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

public class JsonFormatWriter extends FormatWriter<JsonWriter> {
  private static final Moshi MOSHI = new Moshi.Builder().add(DDSpanAdapter.FACTORY).build();

  public static final JsonAdapter<List<DDSpan>> TRACE_ADAPTER =
      MOSHI.adapter(Types.newParameterizedType(List.class, DDSpan.class));
  public static final JsonAdapter<DDSpan> SPAN_ADAPTER = MOSHI.adapter(DDSpan.class);

  public static JsonFormatWriter JSON_WRITER = new JsonFormatWriter();

  @Override
  public void writeKey(final String key, final JsonWriter destination) throws IOException {
    destination.name(key);
  }

  @Override
  public void writeListHeader(final int size, final JsonWriter destination) throws IOException {
    destination.beginArray();
  }

  @Override
  public void writeListFooter(final JsonWriter destination) throws IOException {
    destination.endArray();
  }

  @Override
  public void writeMapHeader(final int size, final JsonWriter destination) throws IOException {
    destination.beginObject();
  }

  @Override
  public void writeMapFooter(final JsonWriter destination) throws IOException {
    destination.endObject();
  }

  @Override
  public void writeString(final String key, final String value, final JsonWriter destination)
      throws IOException {
    destination.name(key);
    destination.value(value);
  }

  @Override
  public void writeShort(final String key, final short value, final JsonWriter destination)
      throws IOException {
    destination.name(key);
    destination.value(value);
  }

  @Override
  public void writeByte(final String key, final byte value, final JsonWriter destination)
      throws IOException {
    destination.name(key);
    destination.value(value);
  }

  @Override
  public void writeInt(final String key, final int value, final JsonWriter destination)
      throws IOException {
    destination.name(key);
    destination.value(value);
  }

  @Override
  public void writeLong(final String key, final long value, final JsonWriter destination)
      throws IOException {
    destination.name(key);
    destination.value(value);
  }

  @Override
  public void writeFloat(final String key, final float value, final JsonWriter destination)
      throws IOException {
    destination.name(key);
    destination.value(value);
  }

  @Override
  public void writeDouble(final String key, final double value, final JsonWriter destination)
      throws IOException {
    destination.name(key);
    destination.value(value);
  }

  @Override
  public void writeBigInteger(
      final String key, final BigInteger value, final JsonWriter destination) throws IOException {
    destination.name(key);
    destination.value(value);
  }

  static class DDSpanAdapter extends JsonAdapter<DDSpan> {
    public static final JsonAdapter.Factory FACTORY =
        new JsonAdapter.Factory() {
          @Override
          public JsonAdapter<?> create(
              final Type type, final Set<? extends Annotation> annotations, final Moshi moshi) {
            final Class<?> rawType = Types.getRawType(type);
            if (rawType.isAssignableFrom(DDSpan.class)) {
              return new DDSpanAdapter();
            }
            return null;
          }
        };

    @Override
    public DDSpan fromJson(final JsonReader reader) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void toJson(final JsonWriter writer, final DDSpan value) throws IOException {
      JSON_WRITER.writeDDSpan(value, writer);
    }
  }
}
