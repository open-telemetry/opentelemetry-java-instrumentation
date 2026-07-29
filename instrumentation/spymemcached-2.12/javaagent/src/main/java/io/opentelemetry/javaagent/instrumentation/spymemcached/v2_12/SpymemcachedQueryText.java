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

  static String create(
      String operationName, Class<?>[] parameterTypes, Object[] args, boolean sanitizationEnabled) {
    int valueIndex =
        OPERATIONS_WITH_VALUE.contains(operationName) ? lastArgumentIndex(parameterTypes) : -1;

    StringBuilder queryText = new StringBuilder(operationName);
    for (int i = 0; i < args.length; i++) {
      Object arg = args[i];

      if (i == valueIndex) {
        if (!append(queryText, sanitizationEnabled ? MASK : String.valueOf(arg))) {
          break;
        }
        continue;
      }
      if (isIgnored(operationName, parameterTypes, i, arg)) {
        continue;
      }
      if (!appendKeys(queryText, arg)) {
        break;
      }
    }
    return queryText.toString();
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

  private static boolean isIgnored(
      String operationName, Class<?>[] parameterTypes, int index, Object arg) {
    // transcoders are not part of the command that is sent to memcached. Whether a parameter is a
    // transcoder is determined from the instrumented method's declared parameter type rather than
    // the runtime value: a Transcoder parameter can legitimately be passed as null, and a stored
    // value could otherwise happen to implement Transcoder itself, either of which would confuse a
    // runtime `instanceof` check.
    if (Transcoder.class.isAssignableFrom(parameterTypes[index])) {
      return true;
    }
    // reading the keys of a bulk operation that was given an iterator would consume the iterator
    // before the instrumented method gets to it, so those keys are left out
    if (arg instanceof Iterator) {
      return true;
    }
    // the deprecated delete(String key, int hold) overload ignores its int argument and delegates
    // to delete(key); the distinct delete(String key, long cas) overload takes a real cas value
    // that is sent to memcached, so only the int-typed parameter is dropped here
    return OPERATION_DELETE.equals(operationName)
        && index == 1
        && parameterTypes[index] == int.class;
  }

  /**
   * Returns the index of the last argument that ends up in the query text, or {@code -1}.
   *
   * <p>Only a trailing {@link Transcoder}-typed parameter is skipped here: operations that carry a
   * value never legitimately declare an {@link Iterator}-typed parameter for that value, so
   * treating it as ignorable (as {@link #isIgnored(String, Class[], int, Object)} does for bulk key
   * operations) would cause the actual value to be mistaken for an earlier argument, e.g. the
   * expiration in {@code set}.
   */
  private static int lastArgumentIndex(Class<?>[] parameterTypes) {
    for (int i = parameterTypes.length - 1; i >= 0; i--) {
      if (!Transcoder.class.isAssignableFrom(parameterTypes[i])) {
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
