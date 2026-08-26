/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.v7_0;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import java.io.IOException;
import java.io.StringWriter;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

/**
 * Masks every literal value in an Elasticsearch search request body while preserving the structure
 * and field names. A sequence of JSON values, such as an NDJSON multi-search request, is sanitized
 * completely and separated with semicolons.
 *
 * <p>When the body is not a valid JSON value or sequence of JSON values, this returns {@code null}
 * so that the caller drops the body rather than capturing it raw.
 */
final class JacksonElasticsearchQuerySanitizer implements UnaryOperator<String> {

  private static final String MASKED_VALUE = "?";
  private static final char QUERY_SEPARATOR = ';';

  static final int MAX_NESTING_DEPTH = 200;

  private static class JsonFactoryHolder {
    static final JsonFactory jsonFactory =
        JsonFactory.builder()
            .streamReadConstraints(
                StreamReadConstraints.builder().maxNestingDepth(MAX_NESTING_DEPTH).build())
            .build();

    private JsonFactoryHolder() {}
  }

  @Override
  @Nullable
  public String apply(String body) {
    JsonFactory jsonFactory = JsonFactoryHolder.jsonFactory;
    StringWriter out = new StringWriter(body.length());
    try (JsonParser parser = jsonFactory.createParser(body)) {
      boolean empty = true;
      while (parser.nextToken() != null) {
        if (!empty) {
          out.write(QUERY_SEPARATOR);
        }
        empty = false;
        try (JsonGenerator generator = jsonFactory.createGenerator(out)) {
          if (!maskValue(parser, generator)) {
            return null;
          }
        }
      }
      if (empty) {
        return null;
      }
    } catch (IOException | RuntimeException ignored) {
      return null;
    }
    return out.toString();
  }

  private static boolean maskValue(JsonParser parser, JsonGenerator generator) throws IOException {
    int depth = 0;
    do {
      switch (parser.currentToken()) {
        case START_OBJECT:
          generator.writeStartObject();
          depth++;
          break;
        case END_OBJECT:
          generator.writeEndObject();
          depth--;
          break;
        case START_ARRAY:
          generator.writeStartArray();
          depth++;
          break;
        case END_ARRAY:
          generator.writeEndArray();
          depth--;
          break;
        case FIELD_NAME:
          generator.writeFieldName(parser.currentName());
          break;
        default:
          generator.writeString(MASKED_VALUE);
          break;
      }
      if (depth == 0) {
        return true;
      }
    } while (parser.nextToken() != null);
    return false;
  }
}
