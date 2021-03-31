/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallWhenTrueDecoratorTest {
  @Mock ElementMatcher<String> delegateMatcher;
  @Mock Runnable callback;

  @Test
  void shouldExecuteCallbackWhenMatcherReturnsTrue() {
    // given
    ElementMatcher<String> matcher = new CallWhenTrueDecorator<>(delegateMatcher, callback);

    given(delegateMatcher.matches("true")).willReturn(true);

    // when
    boolean result = matcher.matches("true");

    // then
    assertTrue(result);
    then(callback).should().run();
  }

  @Test
  void shouldNotExecuteCallbackWhenMatcherReturnsFalse() {
    // given
    ElementMatcher<String> matcher = new CallWhenTrueDecorator<>(delegateMatcher, callback);

    // when
    boolean result = matcher.matches("not really true");

    // then
    assertFalse(result);
    then(callback).should(never()).run();
  }
}
