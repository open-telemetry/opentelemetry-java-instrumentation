/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.common;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import java.util.Arrays;
import java.util.List;
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
        Arguments.of(named("a null value", new Parameter(null, emptyList()))),
        Arguments.of(named("an empty value", new Parameter("", emptyList()))),
        Arguments.of(named("a single key", new Parameter("key<key>", singletonList("key")))),
        Arguments.of(
            named("a single value", new Parameter("value<value>", singletonList("value")))),
        Arguments.of(
            named("a plain string", new Parameter("teststring", singletonList("teststring")))),
        Arguments.of(named("an integer", new Parameter("42", singletonList("42")))),
        Arguments.of(
            named("a base64 value", new Parameter("TeST123==", singletonList("TeST123==")))),
        Arguments.of(
            named(
                "a complex list of args",
                new Parameter(
                    "key<key> aSDFgh4321= 5 test value<val>",
                    Arrays.asList("key", "aSDFgh4321=", "5", "test", "val")))));
  }

  private static class Parameter {
    public final String args;
    public final List<String> result;

    public Parameter(String query, List<String> result) {
      this.args = query;
      this.result = result;
    }
  }
}
