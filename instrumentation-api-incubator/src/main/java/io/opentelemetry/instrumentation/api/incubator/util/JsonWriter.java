package io.opentelemetry.instrumentation.api.incubator.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.ByteArrayOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * A lightweight JSON writer without dependencies. It performs minimal JSON structure checks unless
 * using the lenient mode.
 *
 * <p>Copied from <a href=https://github.com/DataDog/dd-trace-java/blob/master/components/json/src/main/java/datadog/json/JsonWriter.java.>dd-trace-java</a></a>.</p>
 */
public final class JsonWriter implements Flushable, AutoCloseable {
  private static final int INITIAL_CAPACITY = 256;
  private final ByteArrayOutputStream outputStream;
  private final OutputStreamWriter writer;

  private boolean requireComma;

  /**
   * Creates a writer.
   */
  public JsonWriter() {
    this.outputStream = new ByteArrayOutputStream(INITIAL_CAPACITY);
    this.writer = new OutputStreamWriter(this.outputStream, UTF_8);
    this.requireComma = false;
  }

  /**
   * Starts a JSON object.
   *
   * @return This writer instance.
   */
  @CanIgnoreReturnValue
  public JsonWriter beginObject() {
    injectCommaIfNeeded();
    write('{');
    return this;
  }

  /**
   * * Ends the current JSON object.
   *
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter endObject() {
    write('}');
    endsValue();
    return this;
  }

  /**
   * Writes an object property name.
   *
   * @param name The property name.
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter name(String name) {
    if (name == null) {
      throw new IllegalArgumentException("name cannot be null");
    }
    injectCommaIfNeeded();
    writeStringLiteral(name);
    write(':');
    return this;
  }

  /**
   * Writes a {@code null} value.
   *
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter nullValue() {
    injectCommaIfNeeded();
    writeStringRaw("null");
    endsValue();
    return this;
  }

  /**
   * Writes JSON value without escaping it.
   *
   * @param value The JSON value to write.
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter jsonValue(String value) {
    // No structure check here assuming raw JSON is safe to write
    injectCommaIfNeeded();
    writeStringRaw(value);
    endsValue();
    return this;
  }

  /**
   * Writes a boolean value.
   *
   * @param value The value to write.
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter value(boolean value) {
    injectCommaIfNeeded();
    writeStringRaw(value ? "true" : "false");
    endsValue();
    return this;
  }

  /**
   * Writes a string value.
   *
   * @param value The value to write.
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter value(String value) {
    if (value == null) {
      return nullValue();
    }
    injectCommaIfNeeded();
    writeStringLiteral(value);
    endsValue();
    return this;
  }

  /**
   * Writes an integer as a number value.
   *
   * @param value The integer to write.
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter value(int value) {
    injectCommaIfNeeded();
    writeStringRaw(Integer.toString(value));
    endsValue();
    return this;
  }

  /**
   * Writes a long as a number value.
   *
   * @param value The long to write.
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter value(long value) {
    injectCommaIfNeeded();
    writeStringRaw(Long.toString(value));
    endsValue();
    return this;
  }

  /**
   * Writes a float as a number value.
   *
   * @param value The float to write.
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter value(float value) {
    if (Float.isNaN(value)) {
      return nullValue();
    }
    injectCommaIfNeeded();
    writeStringRaw(Float.toString(value));
    endsValue();
    return this;
  }

  /**
   * Writes a double as a number value.
   *
   * @param value The value to write.
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter value(double value) {
    if (Double.isNaN(value)) {
      return nullValue();
    }
    injectCommaIfNeeded();
    writeStringRaw(Double.toString(value));
    endsValue();
    return this;
  }

  /**
   * Starts a JSON array.
   *
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter beginArray() {
    injectCommaIfNeeded();
    write('[');
    return this;
  }

  /**
   * Ends the current JSON array.
   *
   * @return This writer.
   */
  @CanIgnoreReturnValue
  public JsonWriter endArray() {
    endsValue();
    write(']');
    return this;
  }

  /**
   * Gets the JSON String as a UTF-8 byte array.
   *
   * @return The JSON String as a UTF-8 byte array.
   */
  public byte[] toByteArray() {
    flush();
    return this.outputStream.toByteArray();
  }

  @Override
  public String toString() {
    return new String(toByteArray(), UTF_8);
  }

  @Override
  public void flush() {
    try {
      this.writer.flush();
    } catch (IOException ignored) {
      // ignore
    }
  }

  @Override
  public void close() {
    try {
      this.outputStream.close();
      this.writer.close();
    } catch (IOException ignored) {
      // ignore
    }
  }

  private void injectCommaIfNeeded() {
    if (this.requireComma) {
      write(',');
    }
    this.requireComma = false;
  }

  private void endsValue() {
    this.requireComma = true;
  }

  private void write(char ch) {
    try {
      this.writer.write(ch);
    } catch (IOException ignored) {
      // ignore
    }
  }

  private void writeStringLiteral(String str) {
    try {
      this.writer.write('"');

      for (int i = 0; i < str.length(); ++i) {
        char c = str.charAt(i);
        // Escape any char outside ASCII to their Unicode equivalent
        if (c > 127) {
          this.writer.write('\\');
          this.writer.write('u');
          String hexCharacter = Integer.toHexString(c).toUpperCase(ROOT);
          if (c < 4096) {
            this.writer.write('0');
            if (c < 256) {
              this.writer.write('0');
            }
          }
          this.writer.append(hexCharacter);
        } else {
          switch (c) {
            case '"': // Quotation mark
            case '\\': // Reverse solidus
            case '/': // Solidus
              this.writer.write('\\');
              this.writer.write(c);
              break;
            case '\b': // Backspace
              this.writer.write('\\');
              this.writer.write('b');
              break;
            case '\f': // Form feed
              this.writer.write('\\');
              this.writer.write('f');
              break;
            case '\n': // Line feed
              this.writer.write('\\');
              this.writer.write('n');
              break;
            case '\r': // Carriage return
              this.writer.write('\\');
              this.writer.write('r');
              break;
            case '\t': // Horizontal tab
              this.writer.write('\\');
              this.writer.write('t');
              break;
            default:
              this.writer.write(c);
              break;
          }
        }
      }

      this.writer.write('"');
    } catch (IOException ignored) {
      // ignore
    }
  }

  private void writeStringRaw(String str) {
    try {
      this.writer.write(str);
    } catch (IOException ignored) {
      // ignore
    }
  }
}