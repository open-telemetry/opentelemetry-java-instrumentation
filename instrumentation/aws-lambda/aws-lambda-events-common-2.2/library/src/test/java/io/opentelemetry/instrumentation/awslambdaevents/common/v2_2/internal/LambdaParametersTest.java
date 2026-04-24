/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class LambdaParametersTest {

  void onlyContext(Context context) {}

  void noContext(String one) {}

  void contextOnSecond(String one, Context context) {}

  void contextOnThird(String one, String two, Context context) {}

  @Test
  void shouldSetContextOnFirstPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getDeclaredMethod("onlyContext", Context.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context, (o, c) -> o);
    // then
    assertThat(params).containsExactly(context);
  }

  @Test
  void shouldOnlySetInputWhenNoContext() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getDeclaredMethod("noContext", String.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context, (o, c) -> o);
    // then
    assertThat(params).containsExactly("");
  }

  @Test
  void shouldSetContextOnTheSecondPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getDeclaredMethod("contextOnSecond", String.class, Context.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context, (o, c) -> o);
    // then
    assertThat(params).containsExactly("", context);
  }

  @Test
  void shouldSetContextOnTheLastPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method =
        getClass().getDeclaredMethod("contextOnThird", String.class, String.class, Context.class);
    // when
    Object[] params = LambdaParameters.toArray(method, "", context, (o, c) -> o);
    // then
    assertThat(params).containsExactly("", null, context);
  }

  @Test
  void shouldNotResolveInputWhenNoInput() throws NoSuchMethodException {
    // given
    Method method = getClass().getDeclaredMethod("onlyContext", Context.class);
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
    Method method = getClass().getDeclaredMethod("contextOnSecond", String.class, Context.class);
    String givenInput = "testInput";
    // when
    Object resolvedInput =
        LambdaParameters.toInput(
            method,
            new ByteArrayInputStream(SerializationUtil.toJsonData(givenInput)),
            (i, c) -> SerializationUtil.fromJson(i, c));
    // then
    assertThat(resolvedInput).isEqualTo(givenInput);
  }

  @Test
  void shouldResolveInputWithoutContext() throws NoSuchMethodException {
    // given
    Method method = getClass().getDeclaredMethod("noContext", String.class);
    String givenInput = "testInput";
    // when
    Object resolvedInput =
        LambdaParameters.toInput(
            method,
            new ByteArrayInputStream(SerializationUtil.toJsonData(givenInput)),
            (i, c) -> SerializationUtil.fromJson(i, c));
    // then
    assertThat(resolvedInput).isEqualTo(givenInput);
  }

  @Test
  void shouldResolveParametersWhenOnlyContext() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getDeclaredMethod("onlyContext", Context.class);
    // when
    Object[] params = LambdaParameters.toParameters(method, "", context);
    // then
    assertThat(params).containsExactly(context);
  }

  @Test
  void shouldResolveParametersWhenNoContext() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getDeclaredMethod("noContext", String.class);
    // when
    Object[] params = LambdaParameters.toParameters(method, "", context);
    // then
    assertThat(params).containsExactly("");
  }

  @Test
  void shouldResolveParametersWhenContextOnTheSecondPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method = getClass().getDeclaredMethod("contextOnSecond", String.class, Context.class);
    // when
    Object[] params = LambdaParameters.toParameters(method, "", context);
    // then
    assertThat(params).containsExactly("", context);
  }

  @Test
  void shouldResolveParametersWhenContextOnTheLastPosition() throws NoSuchMethodException {
    // given
    Context context = mock(Context.class);
    Method method =
        getClass().getDeclaredMethod("contextOnThird", String.class, String.class, Context.class);
    // when
    Object[] params = LambdaParameters.toParameters(method, "", context);
    // then
    assertThat(params).containsExactly("", null, context);
  }
}
