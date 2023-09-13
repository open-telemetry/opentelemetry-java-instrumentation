/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.google.common.collect.ImmutableList;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LettuceArgSplitterTest {

  @ParameterizedTest
  @MethodSource("providesArguments")
  void testShouldProperlySplitArguments(Parameter parameter) {
    assertThat(LettuceArgSplitter.splitArgs(parameter.args)).isEqualTo(parameter.result);
  }

  private static Stream<Arguments> providesArguments() {
    return Stream.of(
        Arguments.of(named("a null value", new Parameter(null, ImmutableList.of()))),
        Arguments.of(named("an empty value", new Parameter("", ImmutableList.of()))),
        Arguments.of(named("a single key", new Parameter("key<key>", ImmutableList.of("key")))),
        Arguments.of(
            named("a single value", new Parameter("value<value>", ImmutableList.of("value")))),
        Arguments.of(
            named("a plain string", new Parameter("teststring", ImmutableList.of("teststring")))),
        Arguments.of(named("an integer", new Parameter("42", ImmutableList.of("42")))),
        Arguments.of(
            named("a base64 value", new Parameter("TeST123==", ImmutableList.of("TeST123==")))),
        Arguments.of(
            named(
                "a complex list of args",
                new Parameter(
                    "key<key> aSDFgh4321= 5 test value<val>",
                    ImmutableList.of("key", "aSDFgh4321=", "5", "test", "val")))));
  }

  private static class Parameter {
    public final String args;
    public final ImmutableList<String> result;

    public Parameter(String query, ImmutableList<String> result) {
      this.args = query;
      this.result = result;
    }
  }
}
