/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Dubbo status code mapping utility.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboStatusCodeUtil {

  private static final Map<Byte, String> DUBBO2_STATUS_CODES;

  static {
    Map<Byte, String> codes = new HashMap<>();
    codes.put((byte) 20, "OK");
    codes.put((byte) 30, "CLIENT_TIMEOUT");
    codes.put((byte) 31, "SERVER_TIMEOUT");
    codes.put((byte) 35, "CHANNEL_INACTIVE");
    codes.put((byte) 40, "BAD_REQUEST");
    codes.put((byte) 50, "BAD_RESPONSE");
    codes.put((byte) 60, "SERVICE_NOT_FOUND");
    codes.put((byte) 70, "SERVICE_ERROR");
    codes.put((byte) 80, "SERVER_ERROR");
    codes.put((byte) 90, "CLIENT_ERROR");
    codes.put((byte) 100, "SERVER_THREADPOOL_EXHAUSTED_ERROR");
    codes.put((byte) 120, "SERIALIZATION_ERROR");
    DUBBO2_STATUS_CODES = Collections.unmodifiableMap(codes);
  }

  private static final Set<String> DUBBO2_SERVER_ERROR_CODES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "SERVER_ERROR",
                  "SERVER_THREADPOOL_EXHAUSTED_ERROR",
                  "SERVER_TIMEOUT",
                  "SERVICE_ERROR")));

  private static final Set<String> TRIPLE_SERVER_ERROR_CODES =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "DATA_LOSS",
                  "DEADLINE_EXCEEDED",
                  "INTERNAL",
                  "UNAVAILABLE",
                  "UNIMPLEMENTED",
                  "UNKNOWN")));

  public static String dubbo2StatusCodeToString(byte statusCode) {
    String name = DUBBO2_STATUS_CODES.get(statusCode);
    return name != null ? name : "UNKNOWN_STATUS_" + (statusCode & 0xFF);
  }

  public static boolean isDubbo2ServerError(String statusCodeName) {
    return DUBBO2_SERVER_ERROR_CODES.contains(statusCodeName);
  }

  public static boolean isDubbo2ClientError(String statusCodeName) {
    return !"OK".equals(statusCodeName);
  }

  public static boolean isTripleServerError(String statusCodeName) {
    return TRIPLE_SERVER_ERROR_CODES.contains(statusCodeName);
  }

  public static boolean isTripleClientError(String statusCodeName) {
    return !"OK".equals(statusCodeName);
  }

  private DubboStatusCodeUtil() {}
}
