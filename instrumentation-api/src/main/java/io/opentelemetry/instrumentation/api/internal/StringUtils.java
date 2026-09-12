/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

/**
 * Internal string utilities.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class StringUtils {

  /**
   * Returns the longest prefix of {@code value} that fits within {@code maxLength} UTF-16 code
   * units without splitting a valid surrogate pair.
   */
  public static String truncate(String value, int maxLength) {
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, truncationLength(value, maxLength));
  }

  /**
   * Truncates {@code value} to the longest prefix that fits within {@code maxLength} UTF-16 code
   * units without splitting a valid surrogate pair.
   */
  public static void truncate(StringBuilder value, int maxLength) {
    if (value.length() > maxLength) {
      value.setLength(truncationLength(value, maxLength));
    }
  }

  private static int truncationLength(CharSequence value, int maxLength) {
    if (maxLength > 0
        && Character.isHighSurrogate(value.charAt(maxLength - 1))
        && Character.isLowSurrogate(value.charAt(maxLength))) {
      return maxLength - 1;
    }
    return maxLength;
  }

  private StringUtils() {}
}
