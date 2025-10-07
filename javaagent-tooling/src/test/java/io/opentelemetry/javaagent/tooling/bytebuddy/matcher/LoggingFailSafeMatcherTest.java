package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.tooling.bytebuddy.LoggingFailSafeMatcher;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

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
  }

  @Test
  void testMatcherException() {
    ElementMatcher mockMatcher = mock(ElementMatcher.class);
    when(mockMatcher.matches(any())).thenThrow(new RuntimeException("matcher exception"));

    LoggingFailSafeMatcher<Object> matcher = new LoggingFailSafeMatcher<>(mockMatcher, "test");

    assertThatCode(() -> matcher.matches(new Object())).doesNotThrowAnyException();
    assertThat(matcher.matches(new Object())).isFalse();
  }
}
