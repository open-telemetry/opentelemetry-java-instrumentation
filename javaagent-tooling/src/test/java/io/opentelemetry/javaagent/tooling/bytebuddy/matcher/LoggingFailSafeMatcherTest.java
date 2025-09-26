/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.tooling.bytebuddy.LoggingFailSafeMatcher;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class LoggingFailSafeMatcherTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testMatcher(boolean match) {
    @SuppressWarnings("unchecked")
    ElementMatcher<Object> mockMatcher = mock(ElementMatcher.class);
    when(mockMatcher.matches(any())).thenReturn(match);

    LoggingFailSafeMatcher<Object> matcher = new LoggingFailSafeMatcher<>(mockMatcher, "test");
    Object testObject = new Object();

    boolean result = matcher.matches(testObject);

    if (match) {
      assertTrue(result);
    } else {
      assertFalse(result);
    }
  }

  @Test
  void testMatcherException() {
    @SuppressWarnings("unchecked")
    ElementMatcher<Object> mockMatcher = mock(ElementMatcher.class);
    when(mockMatcher.matches(any())).thenThrow(new RuntimeException("matcher exception"));

    LoggingFailSafeMatcher<Object> matcher = new LoggingFailSafeMatcher<>(mockMatcher, "test");
    Object testObject = new Object();

    boolean result = matcher.matches(testObject);

    // Should default to false when exception occurs
    assertFalse(result);
  }
}