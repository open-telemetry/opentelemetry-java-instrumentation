/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

  @Test
  void truncateString() {
    String unchanged = "abc";

    assertThat(StringUtils.truncate(unchanged, 3)).isSameAs(unchanged);
    assertThat(StringUtils.truncate(unchanged, 4)).isSameAs(unchanged);
    assertThat(StringUtils.truncate("abcd", 3)).isEqualTo(unchanged);
    assertThat(StringUtils.truncate("ābc", 2)).isEqualTo("āb");
    assertThat(StringUtils.truncate("a😀b", 1)).isEqualTo("a");
    assertThat(StringUtils.truncate("a😀b", 2)).isEqualTo("a");
    assertThat(StringUtils.truncate("a😀b", 3)).isEqualTo("a😀");
    assertThat(StringUtils.truncate(unchanged, 0)).isEmpty();
  }

  @Test
  void truncateStringPreservesMalformedSurrogates() {
    char highSurrogate = (char) 0xd83d;
    char lowSurrogate = (char) 0xde00;

    assertThat(StringUtils.truncate("a" + highSurrogate + "b", 2)).isEqualTo("a" + highSurrogate);
    assertThat(StringUtils.truncate("a" + lowSurrogate + "b", 2)).isEqualTo("a" + lowSurrogate);
  }

  @Test
  void truncateStringBuilder() {
    StringBuilder value = new StringBuilder("a😀b");

    StringUtils.truncate(value, 2);

    assertThat(value).hasToString("a");
  }

  @Test
  void truncateStringBuffer() {
    StringBuffer value = new StringBuffer("a😀b");

    StringUtils.truncate(value, 2);

    assertThat(value).hasToString("a");
  }

  @Test
  void appendTruncated() {
    StringBuilder target = new StringBuilder("prefix:");

    StringUtils.appendTruncated(target, "a😀b", 2);

    assertThat(target).hasToString("prefix:a");
  }
}
