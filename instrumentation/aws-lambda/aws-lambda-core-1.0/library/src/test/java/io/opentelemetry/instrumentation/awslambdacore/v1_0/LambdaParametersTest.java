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

  public void onlyContext(Context context) {}

  public void contextOnThird(String one, String two, Context context) {}

  @Test
  void shouldSetContextOnFirstPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getMethod("onlyContext", Context.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context);
    // then
    assertThat(params).hasSize(1);
    assertThat(params[0]).isEqualTo(context);
  }

  @Test
  void shouldSetContextOnTheLastPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method =
        getClass().getMethod("contextOnThird", String.class, String.class, Context.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context);
    // then
    assertThat(params).hasSize(3);
    assertThat(params[0]).isEqualTo("");
    assertThat(params[1]).isNull();
    assertThat(params[2]).isEqualTo(context);
  }
}
