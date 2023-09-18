/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Collections.unmodifiableMap;

import io.opentelemetry.api.trace.StatusCode;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

// https://github.com/open-telemetry/semantic-conventions/blob/v1.21.0/docs/http/http-spans.md#status
enum HttpStatusCodeConverter {
  SERVER {

    @Override
    StatusCode getSpanStatus(int responseStatusCode) {
      if (responseStatusCode >= 100 && responseStatusCode < 500) {
        return StatusCode.UNSET;
      }

      return StatusCode.ERROR;
    }

    @Nullable
    @Override
    String getErrorType(int responseStatusCode) {
      return serverErrorTypes.get(responseStatusCode);
    }
  },
  CLIENT {
    @Override
    StatusCode getSpanStatus(int responseStatusCode) {
      if (responseStatusCode >= 100 && responseStatusCode < 400) {
        return StatusCode.UNSET;
      }

      return StatusCode.ERROR;
    }

    @Nullable
    @Override
    String getErrorType(int responseStatusCode) {
      return clientErrorTypes.get(responseStatusCode);
    }
  };

  abstract StatusCode getSpanStatus(int responseStatusCode);

  @Nullable
  abstract String getErrorType(int responseStatusCode);

  private static final Map<Integer, String> serverErrorTypes;
  private static final Map<Integer, String> clientErrorTypes;

  // https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
  static {
    Map<Integer, String> serverPhrases = new HashMap<>();
    serverPhrases.put(500, "Internal Server Error");
    serverPhrases.put(501, "Not Implemented");
    serverPhrases.put(502, "Bad Gateway");
    serverPhrases.put(503, "Service Unavailable");
    serverPhrases.put(504, "Gateway Timeout");
    serverPhrases.put(505, "HTTP Version Not Supported");
    serverPhrases.put(506, "Variant Also Negotiates");
    serverPhrases.put(507, "Insufficient Storage");
    serverPhrases.put(508, "Loop Detected");
    serverPhrases.put(510, "Not Extended");
    serverPhrases.put(511, "Network Authentication Required");
    serverErrorTypes = unmodifiableMap(serverPhrases);

    // include all server error types
    Map<Integer, String> clientPhrases = new HashMap<>(serverPhrases);
    clientPhrases.put(400, "Bad Request");
    clientPhrases.put(401, "Unauthorized");
    clientPhrases.put(402, "Payment Required");
    clientPhrases.put(403, "Forbidden");
    clientPhrases.put(404, "Not Found");
    clientPhrases.put(405, "Method Not Allowed");
    clientPhrases.put(406, "Not Acceptable");
    clientPhrases.put(407, "Proxy Authentication Required");
    clientPhrases.put(408, "Request Timeout");
    clientPhrases.put(409, "Conflict");
    clientPhrases.put(410, "Gone");
    clientPhrases.put(411, "Length Required");
    clientPhrases.put(412, "Precondition Failed");
    clientPhrases.put(413, "Content Too Large");
    clientPhrases.put(414, "URI Too Long");
    clientPhrases.put(415, "Unsupported Media Type");
    clientPhrases.put(416, "Range Not Satisfiable");
    clientPhrases.put(417, "Expectation Failed");
    clientPhrases.put(418, "I'm a teapot");
    clientPhrases.put(421, "Misdirected Request");
    clientPhrases.put(422, "Unprocessable Content");
    clientPhrases.put(423, "Locked");
    clientPhrases.put(424, "Failed Dependency");
    clientPhrases.put(425, "Too Early");
    clientPhrases.put(426, "Upgrade Required");
    clientPhrases.put(428, "Precondition Required");
    clientPhrases.put(429, "Too Many Requests");
    clientPhrases.put(431, "Request Header Fields Too Large");
    clientPhrases.put(451, "Unavailable For Legal Reasons");
    clientErrorTypes = unmodifiableMap(clientPhrases);
  }
}
