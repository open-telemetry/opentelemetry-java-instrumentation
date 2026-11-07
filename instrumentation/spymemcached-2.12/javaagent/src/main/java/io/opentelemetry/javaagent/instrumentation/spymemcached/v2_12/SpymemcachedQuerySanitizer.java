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
    switch (operation) {
      case "getBulk":
        if (hasPerKeyTranscoders(descriptor)) {
          // This overload requests as many keys as there are transcoders; identifying them would
          // consume the iterator.
          return query.toString();
        }
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
        // These take key, expiration, stored value, and an optional transcoder.
        append(query, args[0]);
        append(query, args[1]);
        append(query, "?");
        break;
      case "cas":
        append(query, args[0]);
        append(query, args[1]);
        // A numeric stored value must not be mistaken for expiration; use declared parameter types.
        if (hasCasExpiration(descriptor)) {
          append(query, args[2]);
        }
        append(query, "?");
        break;
      case "append":
      case "prepend":
        // These may start with a primitive long CAS token before the key.
        append(query, args[0]);
        if (args[0] instanceof Long) {
          append(query, args[1]);
        }
        // The appended or prepended value is always masked.
        append(query, "?");
        break;
      case "incr":
      case "decr":
        // These take key, delta, optional initial value, and optional expiration.
        append(query, args[0]);
        append(query, args[1]);
        if (args.length > 2) {
          // The initial value is sensitive, while the following expiration is retained.
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
        // These may have a numeric expiration or deletion delay; ignore transcoders.
        append(query, args[0]);
        if (args.length > 1 && (args[1] instanceof Integer || args[1] instanceof Long)) {
          append(query, args[1]);
        }
        break;
      default:
        // Unknown operations omit arguments to avoid exposing stored values.
        break;
    }
    return query.toString();
  }

  private static boolean hasPerKeyTranscoders(String descriptor) {
    // asyncGetBulk(Collection keys, Iterator transcoders)
    return descriptor.startsWith("(Ljava/util/Collection;Ljava/util/Iterator;");
  }

  private static boolean hasCasExpiration(String descriptor) {
    // cas/asyncCAS(String key, long cas, int expiration, Object value, ...)
    return descriptor.startsWith("(Ljava/lang/String;JI");
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
