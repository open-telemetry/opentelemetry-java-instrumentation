/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal.SerializationUtil;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class LambdaParametersTest {

  public void onlyContext(Context context) {}

  public void noContext(String one) {}

  public void contextOnSecond(String one, Context context) {}

  public void contextOnThird(String one, String two, Context context) {}

  @Test
  void shouldSetContextOnFirstPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getMethod("onlyContext", Context.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context, (o, c) -> o);
    // then
    assertThat(params).hasSize(1);
    assertThat(params[0]).isEqualTo(context);
  }

  @Test
  void shouldOnlySetInputWhenNoContext() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getMethod("noContext", String.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context, (o, c) -> o);
    // then
    assertThat(params).hasSize(1);
    assertThat(params[0]).isEqualTo("");
  }

  @Test
  void shouldSetContextOnTheSecondPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getMethod("contextOnSecond", String.class, Context.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context, (o, c) -> o);
    // then
    assertThat(params).hasSize(2);
    assertThat(params[0]).isEqualTo("");
    assertThat(params[1]).isEqualTo(context);
  }

  @Test
  void shouldSetContextOnTheLastPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method =
        getClass().getMethod("contextOnThird", String.class, String.class, Context.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context, (o, c) -> o);
    // then
    assertThat(params).hasSize(3);
    assertThat(params[0]).isEqualTo("");
    assertThat(params[1]).isNull();
    assertThat(params[2]).isEqualTo(context);
  }

  @Test
  void shouldNotResolveInputWhenNoInput() throws NoSuchMethodException {
    // given
    Method method = getClass().getMethod("onlyContext", Context.class);
    String giveInput = "testInput";
    // when
    Object resolvedInput =
        LambdaParameters.toInput(
            method,
            new ByteArrayInputStream(SerializationUtil.toJsonData(giveInput)),
            (i, c) -> SerializationUtil.fromJson(i, c));
    // then
    assertThat(resolvedInput).isNull();
  }

  @Test
  void shouldResolveInputWithContext() throws NoSuchMethodException {
    // given
    Method method = getClass().getMethod("contextOnSecond", String.class, Context.class);
    String givenInput = "testInput";
    // when
    Object resolvedInput =
        LambdaParameters.toInput(
            method,
            new ByteArrayInputStream(SerializationUtil.toJsonData(givenInput)),
            (i, c) -> SerializationUtil.fromJson(i, c));
    // then
    assertThat(resolvedInput).isNotNull();
    assertThat(resolvedInput).isEqualTo(givenInput);
  }

  @Test
  void shouldResolveInputWithoutContext() throws NoSuchMethodException {
    // given
    Method method = getClass().getMethod("noContext", String.class);
    String givenInput = "testInput";
    // when
    Object resolvedInput =
        LambdaParameters.toInput(
            method,
            new ByteArrayInputStream(SerializationUtil.toJsonData(givenInput)),
            (i, c) -> SerializationUtil.fromJson(i, c));
    // then
    assertThat(resolvedInput).isNotNull();
    assertThat(resolvedInput).isEqualTo(givenInput);
  }

  @Test
  void shouldResolveParametersWhenOnlyContext() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getMethod("onlyContext", Context.class);
    // when
    Object[] params = LambdaParameters.toParameters(method, "", context);
    // then
    assertThat(params).hasSize(1);
    assertThat(params[0]).isEqualTo(context);
  }

  @Test
  void shouldResolveParametersWhenNoContext() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getMethod("noContext", String.class);
    // when
    Object[] params = LambdaParameters.toParameters(method, "", context);
    // then
    assertThat(params).hasSize(1);
    assertThat(params[0]).isEqualTo("");
  }

  @Test
  void shouldResolveParametersWhenContextOnTheSecondPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getMethod("contextOnSecond", String.class, Context.class);
    // when
    Object[] params = LambdaParameters.toParameters(method, "", context);
    // then
    assertThat(params).hasSize(2);
    assertThat(params[0]).isEqualTo("");
    assertThat(params[1]).isEqualTo(context);
  }

  @Test
  void shouldResolveParametersWhenContextOnTheLastPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method =
        getClass().getMethod("contextOnThird", String.class, String.class, Context.class);
    // when
    Object[] params = LambdaParameters.toParameters(method, "", context);
    // then
    assertThat(params).hasSize(3);
    assertThat(params[0]).isEqualTo("");
    assertThat(params[1]).isNull();
    assertThat(params[2]).isEqualTo(context);
  }
}
