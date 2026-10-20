/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common;

import static java.util.Arrays.asList;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

public final class JedisRedisErrorType {
  private static final Set<String> REDIS_ERROR_CODES =
      Collections.unmodifiableSet(
          new HashSet<>(
              asList(
                  "ASK",
                  "AUTH",
                  "BUSY",
                  "BUSYKEY",
                  "BUSYGROUP",
                  "CROSSSLOT",
                  "ERR",
                  "EXECABORT",
                  "LOADING",
                  "MASTERDOWN",
                  "MISCONF",
                  "MOVED",
                  "NOAUTH",
                  "NOGROUP",
                  "NOFUNCTION",
                  "NOPERM",
                  "NOPROTO",
                  "NOREPLICAS",
                  "NOSCRIPT",
                  "NOSUB",
                  "NOTBUSY",
                  "NOTFOUND",
                  "OOM",
                  "READONLY",
                  "STALE",
                  "SYNTAX",
                  "TRYAGAIN",
                  "UNBLOCKED",
                  "WRONGPASS",
                  "WRONGTYPE")));

  private JedisRedisErrorType() {}

  @Nullable
  public static String getErrorType(@Nullable Throwable error) {
    if (error == null) {
      return null;
    }
    String errorCode = getRedisErrorCode(error.getMessage());
    return errorCode != null ? errorCode : error.getClass().getName();
  }

  @Nullable
  private static String getRedisErrorCode(@Nullable String message) {
    if (message == null || message.isEmpty()) {
      return null;
    }
    int separator = message.indexOf(' ');
    String errorCode = separator == -1 ? message : message.substring(0, separator);
    return REDIS_ERROR_CODES.contains(errorCode) ? errorCode : null;
  }
}
