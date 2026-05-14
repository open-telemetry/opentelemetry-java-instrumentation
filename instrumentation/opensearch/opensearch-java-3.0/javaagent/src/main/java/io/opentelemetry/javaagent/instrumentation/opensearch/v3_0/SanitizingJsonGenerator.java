/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import java.math.BigDecimal;
import java.math.BigInteger;

/** A JsonGenerator wrapper that sanitizes literal values by replacing them with "?". */
final class SanitizingJsonGenerator implements JsonGenerator {

  private static final String MASKED_VALUE = "?";

  private final JsonGenerator delegate;

  SanitizingJsonGenerator(JsonGenerator delegate) {
    this.delegate = delegate;
  }

  // Structure methods - delegate and return this for chaining

  @Override
  public JsonGenerator writeStartObject() {
    delegate.writeStartObject();
    return this;
  }

  @Override
  public JsonGenerator writeStartObject(String name) {
    delegate.writeStartObject(name);
    return this;
  }

  @Override
  public JsonGenerator writeStartArray() {
    delegate.writeStartArray();
    return this;
  }

  @Override
  public JsonGenerator writeStartArray(String name) {
    delegate.writeStartArray(name);
    return this;
  }

  @Override
  public JsonGenerator writeEnd() {
    delegate.writeEnd();
    return this;
  }

  @Override
  public JsonGenerator writeKey(String name) {
    delegate.writeKey(name);
    return this;
  }

  // All write() overloads grouped together - sanitize values and return this

  @Override
  public JsonGenerator write(String value) {
    delegate.write(MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(int value) {
    delegate.write(MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(long value) {
    delegate.write(MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(double value) {
    delegate.write(MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(BigInteger value) {
    delegate.write(MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(BigDecimal value) {
    delegate.write(MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(boolean value) {
    delegate.write(MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(JsonValue value) {
    writeJsonValue(value);
    return this;
  }

  @Override
  public JsonGenerator write(String name, String value) {
    delegate.write(name, MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(String name, int value) {
    delegate.write(name, MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(String name, long value) {
    delegate.write(name, MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(String name, double value) {
    delegate.write(name, MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(String name, BigInteger value) {
    delegate.write(name, MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(String name, BigDecimal value) {
    delegate.write(name, MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(String name, boolean value) {
    delegate.write(name, MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator write(String name, JsonValue value) {
    delegate.writeKey(name);
    writeJsonValue(value);
    return this;
  }

  // All writeNull() overloads grouped together

  @Override
  public JsonGenerator writeNull() {
    delegate.write(MASKED_VALUE);
    return this;
  }

  @Override
  public JsonGenerator writeNull(String name) {
    delegate.write(name, MASKED_VALUE);
    return this;
  }

  private void writeJsonValue(JsonValue value) {
    switch (value.getValueType()) {
      case OBJECT:
        delegate.writeStartObject();
        value.asJsonObject().forEach((k, v) -> write(k, v));
        delegate.writeEnd();
        break;
      case ARRAY:
        delegate.writeStartArray();
        value.asJsonArray().forEach(this::write);
        delegate.writeEnd();
        break;
      case STRING:
      case NUMBER:
      case TRUE:
      case FALSE:
      case NULL:
        delegate.write(MASKED_VALUE);
        break;
    }
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public void flush() {
    delegate.flush();
  }
}
