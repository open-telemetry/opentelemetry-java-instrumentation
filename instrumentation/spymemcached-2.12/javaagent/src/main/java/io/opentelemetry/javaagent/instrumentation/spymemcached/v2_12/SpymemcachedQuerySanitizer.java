/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.instrumentation.api.internal.StringUtils.appendTruncated;
import static java.util.Arrays.asList;

import java.util.Collection;
import javax.annotation.Nullable;

final class SpymemcachedQuerySanitizer {
  private static final int LIMIT = 32 * 1024;

  static String sanitize(String operation, String descriptor, Object[] args) {
    StringBuilder query = new StringBuilder(operation);
    if (args.length == 0) {
      return query.toString();
    }
    if (operation.equals("getBulk")
        && descriptor.startsWith("(Ljava/util/Collection;Ljava/util/Iterator;")) {
      return query.toString();
    }
    switch (operation) {
      case "getBulk":
        Object keys = args[0];
        if (keys instanceof Collection) {
          return sanitizeBulk((Collection<?>) keys);
        } else {
          // The transcoder precedes the keys in the transcoder/varargs overload.
          keys = keys instanceof String[] || args.length == 1 ? keys : args[1];
          if (keys instanceof String[]) {
            return sanitizeBulk(asList((String[]) keys));
          }
        }
        break;
      case "set":
      case "add":
      case "replace":
        append(query, args[0]);
        append(query, args[1]);
        append(query, "?");
        break;
      case "cas":
        append(query, args[0]);
        append(query, args[1]);
        // Use the declared primitive parameter, not the value's runtime type.
        if (descriptor.startsWith("(Ljava/lang/String;JI")) {
          append(query, args[2]);
        }
        append(query, "?");
        break;
      case "append":
      case "prepend":
        append(query, args[0]);
        if (args[0] instanceof Long) {
          append(query, args[1]);
        }
        append(query, "?");
        break;
      case "incr":
      case "decr":
        append(query, args[0]);
        append(query, args[1]);
        if (args.length > 2) {
          append(query, "?");
          if (args.length > 3) {
            append(query, args[3]);
          }
        }
        break;
      case "get":
      case "gets":
      case "getAndTouch":
      case "touch":
      case "delete":
        append(query, args[0]);
        if (args.length > 1 && (args[1] instanceof Integer || args[1] instanceof Long)) {
          append(query, args[1]);
        }
        break;
      default:
        break;
    }
    return query.toString();
  }

  static String sanitizeBulk(Iterable<?> keys) {
    StringBuilder query = new StringBuilder("getBulk");
    for (Object key : keys) {
      append(query, key);
      if (query.length() >= LIMIT) {
        break;
      }
    }
    return query.toString();
  }

  private static void append(StringBuilder query, @Nullable Object arg) {
    if (query.length() >= LIMIT) {
      return;
    }
    if (arg instanceof String || arg instanceof Integer || arg instanceof Long) {
      query.append(' ');
      appendTruncated(query, arg.toString(), LIMIT - query.length());
    }
  }

  private SpymemcachedQuerySanitizer() {}
}
