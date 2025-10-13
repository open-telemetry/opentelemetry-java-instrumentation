/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class SqlCommenterUtil {

  /**
   * Append comment containing tracing information at the end of the query. See <a
   * href="https://google.github.io/sqlcommenter/spec/">sqlcommenter</a> for the description of the
   * algorithm.
   */
  public static String processQuery(String query) {
    if (!Span.current().getSpanContext().isValid()) {
      return query;
    }
    // skip queries that contain comments
    if (containsSqlComment(query)) {
      return query;
    }

    class State {
      @Nullable String traceparent;
      @Nullable String tracestate;
    }

    State state = new State();

    W3CTraceContextPropagator.getInstance()
        .inject(
            Context.current(),
            state,
            (carrier, key, value) -> {
              if (carrier == null) {
                return;
              }
              if ("traceparent".equals(key)) {
                carrier.traceparent = value;
              } else if ("tracestate".equals(key)) {
                carrier.tracestate = value;
              }
            });
    try {
      // we know that the traceparent doesn't contain anything that needs to be encoded
      query += " /*traceparent='" + state.traceparent + "'";
      if (state.tracestate != null) {
        query += ", tracestate=" + serialize(state.tracestate);
      }
      query += "*/";
    } catch (UnsupportedEncodingException exception) {
      // this exception should never happen as UTF-8 encoding is always available
    }
    return query;
  }

  private static boolean containsSqlComment(String query) {
    return query.contains("--") || query.contains("/*");
  }

  private static String serialize(String value) throws UnsupportedEncodingException {
    // specification requires percent encoding, here we use the java build in url encoder that
    // encodes space as '+' instead of '%20' as required
    String result = URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    // specification requires escaping ' with a backslash, we skip this because URLEncoder already
    // encodes the '
    return "'" + result + "'";
  }

  private SqlCommenterUtil() {}
}
