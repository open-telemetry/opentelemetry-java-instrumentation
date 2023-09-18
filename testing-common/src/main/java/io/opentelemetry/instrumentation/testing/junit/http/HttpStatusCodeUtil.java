/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.unmodifiableMap;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public final class HttpStatusCodeUtil {

  private static final Map<Integer, String> errorTypes;

  static {
    Map<Integer, String> phrases = new HashMap<>();
    phrases.put(400, "Bad Request");
    phrases.put(401, "Unauthorized");
    phrases.put(402, "Payment Required");
    phrases.put(403, "Forbidden");
    phrases.put(404, "Not Found");
    phrases.put(405, "Method Not Allowed");
    phrases.put(406, "Not Acceptable");
    phrases.put(407, "Proxy Authentication Required");
    phrases.put(408, "Request Timeout");
    phrases.put(409, "Conflict");
    phrases.put(410, "Gone");
    phrases.put(411, "Length Required");
    phrases.put(412, "Precondition Failed");
    phrases.put(413, "Content Too Large");
    phrases.put(414, "URI Too Long");
    phrases.put(415, "Unsupported Media Type");
    phrases.put(416, "Range Not Satisfiable");
    phrases.put(417, "Expectation Failed");
    phrases.put(418, "I'm a teapot");
    phrases.put(421, "Misdirected Request");
    phrases.put(422, "Unprocessable Content");
    phrases.put(423, "Locked");
    phrases.put(424, "Failed Dependency");
    phrases.put(425, "Too Early");
    phrases.put(426, "Upgrade Required");
    phrases.put(428, "Precondition Required");
    phrases.put(429, "Too Many Requests");
    phrases.put(431, "Request Header Fields Too Large");
    phrases.put(451, "Unavailable For Legal Reasons");
    phrases.put(500, "Internal Server Error");
    phrases.put(501, "Not Implemented");
    phrases.put(502, "Bad Gateway");
    phrases.put(503, "Service Unavailable");
    phrases.put(504, "Gateway Timeout");
    phrases.put(505, "HTTP Version Not Supported");
    phrases.put(506, "Variant Also Negotiates");
    phrases.put(507, "Insufficient Storage");
    phrases.put(508, "Loop Detected");
    phrases.put(510, "Not Extended");
    phrases.put(511, "Network Authentication Required");
    errorTypes = unmodifiableMap(phrases);
  }

  @Nullable
  public static String getErrorType(Integer statusCode) {
    return errorTypes.get(statusCode);
  }

  public static void assertErrorTypeForStatusCode(Attributes attributes, int statusCode) {
    if (SemconvStability.emitStableHttpSemconv()) {
      String errorType = errorTypes.get(statusCode);
      if (errorType == null) {
        assertThat(attributes).doesNotContainKey(stringKey("error.type"));
      } else {
        assertThat(attributes).containsEntry(stringKey("error.type"), errorType);
      }
    }
  }

  private HttpStatusCodeUtil() {}
}
