/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * A Jackson JsonGenerator wrapper that sanitizes all literal values by replacing them with "?".
 * This is used to sanitize OpenSearch query bodies before they are captured as span attributes,
 * ensuring that sensitive data is not exposed in telemetry.
 */
final class SanitizingJacksonJsonGenerator extends JsonGeneratorDelegate {

  private static final String MASKED_VALUE = "?";

  SanitizingJacksonJsonGenerator(JsonGenerator delegate) {
    super(delegate);
  }

  // String value methods - sanitize

  @Override
  public void writeString(String value) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  @Override
  public void writeString(char[] buffer, int offset, int length) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  @Override
  public void writeRawUTF8String(byte[] buffer, int offset, int length) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  @Override
  public void writeUTF8String(byte[] buffer, int offset, int length) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  // Number value methods - sanitize

  @Override
  public void writeNumber(int value) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  @Override
  public void writeNumber(long value) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  @Override
  public void writeNumber(float value) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  @Override
  public void writeNumber(double value) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  @Override
  public void writeNumber(BigInteger value) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  @Override
  public void writeNumber(BigDecimal value) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  @Override
  public void writeNumber(String encodedValue) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  // Boolean value methods - sanitize

  @Override
  public void writeBoolean(boolean value) throws IOException {
    delegate.writeString(MASKED_VALUE);
  }

  // Null value methods - sanitize

  @Override
  public void writeNull() throws IOException {
    delegate.writeString(MASKED_VALUE);
  }
}
