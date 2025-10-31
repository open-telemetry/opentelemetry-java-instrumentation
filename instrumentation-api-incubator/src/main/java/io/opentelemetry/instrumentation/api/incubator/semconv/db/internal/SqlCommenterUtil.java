/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class SqlCommenterUtil {

  /**
   * Append comment containing tracing information to the query. See <a
   * href="https://google.github.io/sqlcommenter/spec/">sqlcommenter</a> for the description of the
   * algorithm.
   */
  public static String processQuery(String query, TextMapPropagator propagator, boolean prepend) {
    if (!Span.current().getSpanContext().isValid()) {
      return query;
    }
    // skip queries that contain comments
    if (containsSqlComment(query)) {
      return query;
    }

    Map<String, String> state = new LinkedHashMap<>();
    propagator.inject(
        Context.current(),
        state,
        (carrier, key, value) -> {
          if (carrier == null) {
            return;
          }
          carrier.put(key, value);
        });

    if (state.isEmpty()) {
      return query;
    }

    StringBuilder stringBuilder = new StringBuilder("/*");
    try {
      for (Iterator<Map.Entry<String, String>> iterator = state.entrySet().iterator();
          iterator.hasNext(); ) {
        Map.Entry<String, String> entry = iterator.next();
        stringBuilder
            .append(serialize(entry.getKey()))
            .append("='")
            .append(serialize(entry.getValue()))
            .append("'");
        if (iterator.hasNext()) {
          stringBuilder.append(", ");
        }
      }
    } catch (UnsupportedEncodingException exception) {
      // this exception should never happen as UTF-8 encoding is always available
    }
    stringBuilder.append("*/");

    return prepend ? stringBuilder + " " + query : query + " " + stringBuilder;
  }

  private static boolean containsSqlComment(String query) {
    return query.contains("--") || query.contains("/*");
  }

  private static String serialize(String value) throws UnsupportedEncodingException {
    // specification requires percent encoding, here we use the java built in url encoder that
    // encodes space as '+' instead of '%20' as required
    // specification requires escaping ' with a backslash, we skip this because URLEncoder already
    // encodes the '
    return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
  }

  private SqlCommenterUtil() {}
}
