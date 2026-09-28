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
   * Returns the longest prefix whose {@link String#length()} does not exceed {@code maxLength}. If
   * truncating at the limit would split a surrogate pair, the pair is omitted.
   */
  public static String truncate(String value, int maxLength) {
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, truncationLength(value, maxLength));
  }

  /**
   * Truncates {@code value} so that its length does not exceed {@code maxLength}. If the limit
   * would split a surrogate pair, the pair is omitted.
   */
  public static void truncate(StringBuilder value, int maxLength) {
    if (value.length() > maxLength) {
      value.setLength(truncationLength(value, maxLength));
    }
  }

  /**
   * Truncates {@code value} so that its length does not exceed {@code maxLength}. If the limit
   * would split a surrogate pair, the pair is omitted.
   */
  public static void truncate(StringBuffer value, int maxLength) {
    if (value.length() > maxLength) {
      value.setLength(truncationLength(value, maxLength));
    }
  }

  /**
   * Appends the longest prefix of {@code value} whose length does not exceed {@code maxLength}. If
   * the limit would split a surrogate pair, the pair is omitted.
   */
  public static void appendTruncated(StringBuilder target, String value, int maxLength) {
    int length = value.length() <= maxLength ? value.length() : truncationLength(value, maxLength);
    target.append(value, 0, length);
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
