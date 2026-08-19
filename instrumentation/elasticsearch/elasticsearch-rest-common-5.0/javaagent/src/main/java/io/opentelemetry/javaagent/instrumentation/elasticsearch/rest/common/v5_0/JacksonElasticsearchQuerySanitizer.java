/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import io.opentelemetry.javaagent.bootstrap.elasticsearch.ElasticsearchQuerySanitizerAccess;
import java.io.IOException;
import java.io.StringWriter;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

/**
 * Masks every literal value in an Elasticsearch search request body while preserving the structure
 * and the field names, so that {@code {"query":{"match":{"title":"secret user data"}}}} becomes
 * {@code {"query":{"match":{"title":"?"}}}}. A sequence of JSON values, such as the header and body
 * lines of an NDJSON multi-search request, is sanitized completely and separated with semicolons.
 *
 * <p>This runs in the agent class loader, which is the only place jackson-core is visible.
 * Instrumentation reaches it through {@link ElasticsearchQuerySanitizerAccess}.
 *
 * <p>When the body is not a valid JSON value or sequence of JSON values, this returns {@code null}
 * so that the caller drops the body rather than capturing it raw.
 */
final class JacksonElasticsearchQuerySanitizer implements UnaryOperator<String> {

  private static final String MASKED_VALUE = "?";
  private static final char QUERY_SEPARATOR = ';';

  // Rejects a body nested more deeply than this. The parser and the generator each hold one nesting
  // context per level, so an absurdly deep body costs memory and time on the application's own
  // thread for a query nobody meant to send. Real Elasticsearch query bodies nest at most a few
  // dozen levels, so this sits well above any realistic query.
  static final int MAX_NESTING_DEPTH = 200;

  // held in a nested class so that applications that never send an Elasticsearch search query do
  // not pay for initializing Jackson
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
        // a generator per value keeps Jackson from inserting its own root value separator; closing
        // it flushes into out, and closing a StringWriter does nothing
        try (JsonGenerator generator = jsonFactory.createGenerator(out)) {
          if (!maskValue(parser, generator)) {
            return null;
          }
        }
      }
      if (empty) {
        // the body held no JSON value at all
        return null;
      }
    } catch (IOException | RuntimeException ignored) {
      // the body could not be sanitized: it is not valid JSON, or it is nested more deeply than
      // MAX_NESTING_DEPTH. Either way it must not be captured raw, so drop it instead.
      return null;
    }
    return out.toString();
  }

  /**
   * Copies the JSON value starting at the parser's current token to the generator, masking every
   * scalar. Returns {@code false} if the value ended before its containers were closed.
   */
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
          // field names are structure, not data, so they are kept
          generator.writeFieldName(parser.currentName());
          break;
        default:
          // every string, number, boolean and null in value position is masked
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
