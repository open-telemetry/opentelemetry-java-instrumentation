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
    assertThat(StringUtils.truncate("abcd", 3)).isEqualTo("abc");
    assertThat(StringUtils.truncate("ābc", 2)).isEqualTo("āb");
    assertThat(StringUtils.truncate("a\uD83D\uDE00b", 1)).isEqualTo("a");
    assertThat(StringUtils.truncate("a\uD83D\uDE00b", 2)).isEqualTo("a");
    assertThat(StringUtils.truncate("a\uD83D\uDE00b", 3)).isEqualTo("a\uD83D\uDE00");
    assertThat(StringUtils.truncate("abc", 0)).isEmpty();
  }

  @Test
  void truncateStringPreservesMalformedSurrogates() {
    assertThat(StringUtils.truncate("a\uD83Db", 2)).isEqualTo("a\uD83D");
    assertThat(StringUtils.truncate("a\uDE00b", 2)).isEqualTo("a\uDE00");
  }

  @Test
  void truncateStringBuilder() {
    StringBuilder value = new StringBuilder("a\uD83D\uDE00b");

    StringUtils.truncate(value, 2);

    assertThat(value).hasToString("a");
  }
}
