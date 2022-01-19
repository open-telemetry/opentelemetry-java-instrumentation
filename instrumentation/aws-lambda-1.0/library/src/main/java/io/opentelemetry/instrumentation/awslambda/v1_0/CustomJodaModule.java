/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.opentelemetry.testing.internal.jackson.core.JsonTokenId;
import java.io.IOException;
import javax.annotation.Nullable;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Jackson module to be able to properly parse standard events as provided by <a
 * href="https://github.com/aws/aws-lambda-java-libs/tree/master/aws-lambda-java-events">AWS</a>.
 *
 * <p>It enables parsing of {@link DateTime} which is used by some standard events. A custom module
 * was used as opposed to Jackson standard module for parsing Joda to avoid adding more libraries to
 * the Java Agent.
 *
 * <p>Supporting custom POJOs using Joda is out of the scope of this class.
 */
class CustomJodaModule extends SimpleModule {
  public CustomJodaModule() {
    super();
    addDeserializer(DateTime.class, new DateTimeDeserialiser());
  }

  @Override
  public String getModuleName() {
    return getClass().getSimpleName();
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    return this == o;
  }

  private static class DateTimeDeserialiser extends JsonDeserializer<DateTime> {
    private final DateTimeFormatter dateFormatter =
        DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    @Override
    public DateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      if (JsonTokenId.ID_STRING != p.getCurrentTokenId()) {
        throw new IllegalArgumentException("Only stream input is accepted");
      }

      String value = p.getText().trim();
      return value.isEmpty() ? null : dateFormatter.parseDateTime(value);
    }
  }
}
