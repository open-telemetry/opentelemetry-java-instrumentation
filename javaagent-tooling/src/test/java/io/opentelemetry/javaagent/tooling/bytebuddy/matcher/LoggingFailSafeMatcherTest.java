/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.tooling.bytebuddy.LoggingFailSafeMatcher;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings({"unchecked", "rawtypes"})
class LoggingFailSafeMatcherTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testMatcher(boolean match) {
    ElementMatcher mockMatcher = mock(ElementMatcher.class);
    when(mockMatcher.matches(any())).thenReturn(match);

    LoggingFailSafeMatcher<Object> matcher = new LoggingFailSafeMatcher<>(mockMatcher, "test");

    boolean result = matcher.matches(new Object());

    assertThat(result).isEqualTo(match);
    verify(mockMatcher, times(1)).matches(any());
    verifyNoMoreInteractions(mockMatcher);
  }

  @Test
  void testMatcherException() {
    ElementMatcher mockMatcher = mock(ElementMatcher.class);
    when(mockMatcher.matches(any())).thenThrow(new RuntimeException("matcher exception"));

    LoggingFailSafeMatcher<Object> matcher = new LoggingFailSafeMatcher<>(mockMatcher, "test");

    assertThat(matcher.matches(new Object())).isFalse();
    verify(mockMatcher, times(1)).matches(any());
    verifyNoMoreInteractions(mockMatcher);
  }
}
