/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
      String operationName, String methodDescriptor, Object[] args, boolean sanitizationEnabled) {
    DescriptorInfo descriptorInfo = DescriptorInfo.get(methodDescriptor);
    int valueIndex =
        OPERATIONS_WITH_VALUE.contains(operationName)
            ? descriptorInfo.lastNonTranscoderIndex()
            : -1;

    StringBuilder queryText = new StringBuilder(operationName);

    // For append(long cas, String key, T val) and prepend(long cas, String key, T val),
    // args[0] is the CAS value (long) and args[1] is the key. Put key first to match
    // Memcached convention and other operations like cas <key> <cas> <val>.
    if (descriptorInfo.isAppendOrPrependWithCas(operationName)) {
      appendArg(
          queryText, operationName, 1, args[1], valueIndex, descriptorInfo, sanitizationEnabled);
      appendArg(
          queryText, operationName, 0, args[0], valueIndex, descriptorInfo, sanitizationEnabled);
      for (int i = 2; i < args.length; i++) {
        if (!appendArg(
            queryText,
            operationName,
            i,
            args[i],
            valueIndex,
            descriptorInfo,
            sanitizationEnabled)) {
          break;
        }
      }
      return queryText.toString();
    }

    for (int i = 0; i < args.length; i++) {
      if (!appendArg(
          queryText, operationName, i, args[i], valueIndex, descriptorInfo, sanitizationEnabled)) {
        break;
      }
    }
    return queryText.toString();
  }

  private static boolean appendArg(
      StringBuilder queryText,
      String operationName,
      int index,
      Object arg,
      int valueIndex,
      DescriptorInfo descriptorInfo,
      boolean sanitizationEnabled) {
    if (index == valueIndex) {
      return append(queryText, sanitizationEnabled ? MASK : String.valueOf(arg));
    }
    if (descriptorInfo.isIgnored(operationName, index, arg)) {
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

  /** Returns {@code false} when the limit was reached and the query text was truncated. */
  private static boolean append(StringBuilder queryText, String value) {
    queryText.append(' ').append(value);
    if (queryText.length() > LIMIT) {
      queryText.setLength(LIMIT);
      return false;
    }
    return true;
  }

  static class DescriptorInfo {
    private static final Map<String, DescriptorInfo> CACHE = new ConcurrentHashMap<>();

    public static DescriptorInfo get(String methodDescriptor) {
      return CACHE.computeIfAbsent(methodDescriptor, DescriptorInfo::parse);
    }

    private final boolean[] isTranscoder;
    private final boolean isParam0Long;
    private final boolean isParam1Int;
    private final int lastNonTranscoderIndex;

    private DescriptorInfo(
        boolean[] isTranscoder,
        boolean isParam0Long,
        boolean isParam1Int,
        int lastNonTranscoderIndex) {
      this.isTranscoder = isTranscoder;
      this.isParam0Long = isParam0Long;
      this.isParam1Int = isParam1Int;
      this.lastNonTranscoderIndex = lastNonTranscoderIndex;
    }

    public boolean isAppendOrPrependWithCas(String operationName) {
      return ("append".equals(operationName) || "prepend".equals(operationName)) && isParam0Long;
    }

    public boolean isIgnored(String operationName, int index, Object arg) {
      if (index < isTranscoder.length && isTranscoder[index]) {
        return true;
      }
      if (arg instanceof Iterator) {
        return true;
      }
      return OPERATION_DELETE.equals(operationName) && index == 1 && isParam1Int;
    }

    public int lastNonTranscoderIndex() {
      return lastNonTranscoderIndex;
    }

    private static DescriptorInfo parse(String descriptor) {
      List<String> paramDescriptors = parseParamDescriptors(descriptor);
      int count = paramDescriptors.size();
      boolean[] isTranscoder = new boolean[count];
      int lastNonTranscoder = -1;

      for (int i = 0; i < count; i++) {
        String param = paramDescriptors.get(i);
        if (param.endsWith("Transcoder;")) {
          isTranscoder[i] = true;
        } else {
          lastNonTranscoder = i;
        }
      }

      boolean isParam0Long = count > 0 && "J".equals(paramDescriptors.get(0));
      boolean isParam1Int = count > 1 && "I".equals(paramDescriptors.get(1));

      return new DescriptorInfo(isTranscoder, isParam0Long, isParam1Int, lastNonTranscoder);
    }

    private static List<String> parseParamDescriptors(String descriptor) {
      List<String> params = new ArrayList<>();
      int start = descriptor.indexOf('(');
      int end = descriptor.indexOf(')');
      if (start == -1 || end == -1 || start >= end) {
        return params;
      }

      int i = start + 1;
      while (i < end) {
        char c = descriptor.charAt(i);
        if (c == 'L') {
          int semi = descriptor.indexOf(';', i);
          if (semi == -1 || semi > end) {
            break;
          }
          params.add(descriptor.substring(i, semi + 1));
          i = semi + 1;
        } else if (c == '[') {
          int arrayStart = i;
          while (i < end && descriptor.charAt(i) == '[') {
            i++;
          }
          if (i < end && descriptor.charAt(i) == 'L') {
            int semi = descriptor.indexOf(';', i);
            if (semi == -1 || semi > end) {
              break;
            }
            params.add(descriptor.substring(arrayStart, semi + 1));
            i = semi + 1;
          } else if (i < end) {
            params.add(descriptor.substring(arrayStart, i + 1));
            i++;
          }
        } else {
          params.add(String.valueOf(c));
          i++;
        }
      }
      return params;
    }
  }

  private SpymemcachedQueryText() {}
}
