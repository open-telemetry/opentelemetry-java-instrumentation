/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Arrays.asList;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import net.spy.memcached.transcoders.Transcoder;

/**
 * Builds the query text of a memcached command from the arguments of the instrumented {@code
 * MemcachedClient} method.
 *
 * <p>Examples:
 *
 * <table>
 *   <tr>
 *     <th>Call</th>
 *     <th>Query text</th>
 *   </tr>
 *   <tr>
 *     <td>{@code asyncGet("my-key")}</td>
 *     <td>{@code get my-key}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code set("my-key", 3600, "my-value")}</td>
 *     <td>{@code set my-key 3600 ?}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code asyncGetBulk(asList("key1", "key2"))}</td>
 *     <td>{@code getBulk key1 key2}</td>
 *   </tr>
 * </table>
 */
final class SpymemcachedQueryText {

  // max length of the query text, longer text is truncated to this length
  static final int LIMIT = 32 * 1024;

  private static final String MASK = "?";

  // operations that carry a value that is stored in memcached; the value is always the last
  // argument, and is masked unless sanitization is turned off
  private static final Set<String> OPERATIONS_WITH_VALUE =
      new HashSet<>(asList("set", "add", "replace", "append", "prepend", "cas"));

  private static final String OPERATION_DELETE = "delete";

  static String create(String operationName, Object[] args, boolean sanitizationEnabled) {
    int valueIndex = OPERATIONS_WITH_VALUE.contains(operationName) ? lastArgumentIndex(args) : -1;

    StringBuilder queryText = new StringBuilder(operationName);

    // For append(long cas, String key, T val) and prepend(long cas, String key, T val),
    // args[0] is the CAS value (Long) and args[1] is the key. Put key first to match
    // Memcached convention and other operations like cas <key> <cas> <val>.
    if (isAppendOrPrependWithCas(operationName, args)) {
      appendArg(queryText, operationName, 1, args[1], valueIndex, sanitizationEnabled);
      appendArg(queryText, operationName, 0, args[0], valueIndex, sanitizationEnabled);
      for (int i = 2; i < args.length; i++) {
        if (!appendArg(queryText, operationName, i, args[i], valueIndex, sanitizationEnabled)) {
          break;
        }
      }
      return queryText.toString();
    }

    for (int i = 0; i < args.length; i++) {
      if (!appendArg(queryText, operationName, i, args[i], valueIndex, sanitizationEnabled)) {
        break;
      }
    }
    return queryText.toString();
  }

  private static boolean isAppendOrPrependWithCas(String operationName, Object[] args) {
    return ("append".equals(operationName) || "prepend".equals(operationName))
        && args.length >= 2
        && args[0] instanceof Long;
  }

  private static boolean appendArg(
      StringBuilder queryText,
      String operationName,
      int index,
      Object arg,
      int valueIndex,
      boolean sanitizationEnabled) {
    if (index == valueIndex) {
      return append(queryText, sanitizationEnabled ? MASK : String.valueOf(arg));
    }
    if (isIgnored(operationName, index, arg)) {
      return true;
    }
    return appendKeys(queryText, arg);
  }

  /** Appends {@code arg}, expanding it if it holds the keys of a bulk operation. */
  private static boolean appendKeys(StringBuilder queryText, Object arg) {
    if (arg instanceof Collection) {
      for (Object key : (Collection<?>) arg) {
        if (!append(queryText, String.valueOf(key))) {
          return false;
        }
      }
      return true;
    }
    if (arg instanceof String[]) {
      for (String key : (String[]) arg) {
        if (!append(queryText, key)) {
          return false;
        }
      }
      return true;
    }
    return append(queryText, String.valueOf(arg));
  }

  private static boolean isIgnored(String operationName, int index, Object arg) {
    if (arg instanceof Transcoder) {
      return true;
    }
    if (arg instanceof Iterator) {
      return true;
    }
    // the deprecated delete(String key, int hold) overload ignores its int argument and delegates
    // to delete(key); the distinct delete(String key, long cas) overload takes a real cas value
    // that is sent to memcached, so only the int-typed parameter is dropped here
    return OPERATION_DELETE.equals(operationName) && index == 1 && arg instanceof Integer;
  }

  /** Returns the index of the last argument that ends up in the query text, or {@code -1}. */
  private static int lastArgumentIndex(Object[] args) {
    for (int i = args.length - 1; i >= 0; i--) {
      if (args[i] != null && !(args[i] instanceof Transcoder)) {
        return i;
      }
    }
    return -1;
  }

  /** Returns {@code false} when the limit was reached and the query text was truncated. */
  private static boolean append(StringBuilder queryText, String value) {
    queryText.append(' ').append(value);
    if (queryText.length() > LIMIT) {
      queryText.setLength(LIMIT);
      return false;
    }
    return true;
  }

  private SpymemcachedQueryText() {}
}
