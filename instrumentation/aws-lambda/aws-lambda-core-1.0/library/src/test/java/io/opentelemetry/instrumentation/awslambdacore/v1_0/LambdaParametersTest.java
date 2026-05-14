/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class LambdaParametersTest {

  void onlyContext(Context context) {}

  void contextOnThird(String one, String two, Context context) {}

  @Test
  void shouldSetContextOnFirstPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getDeclaredMethod("onlyContext", Context.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context);
    // then
    assertThat(params).containsExactly(context);
  }

  @Test
  void shouldSetContextOnTheLastPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method =
        getClass().getDeclaredMethod("contextOnThird", String.class, String.class, Context.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context);
    // then
    assertThat(params).containsExactly("", null, context);
  }
}
