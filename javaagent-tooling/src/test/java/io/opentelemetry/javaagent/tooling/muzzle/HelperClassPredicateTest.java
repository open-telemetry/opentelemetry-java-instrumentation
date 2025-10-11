/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HelperClassPredicateTest {

  private static Stream<Arguments> shouldCollectReferencesForProvider() {
    return Stream.of(
        // desc, className
        Arguments.of(
            "javaagent instrumentation class",
            "io.opentelemetry.javaagent.instrumentation.some_instrumentation.Advice"),
        Arguments.of(
            "library instrumentation class", "io.opentelemetry.instrumentation.LibraryClass"),
        Arguments.of(
            "additional library instrumentation class",
            "com.example.instrumentation.library.ThirdPartyExternalInstrumentation"));
  }

  @ParameterizedTest(name = "should collect references for {0}")
  @MethodSource("shouldCollectReferencesForProvider")
  void shouldCollectReferencesFor(String desc, String className) {
    Predicate<String> additionalLibraryPredicate =
        name -> name.startsWith("com.example.instrumentation.library");
    HelperClassPredicate predicate = new HelperClassPredicate(additionalLibraryPredicate);

    assertThat(predicate.isHelperClass(className)).isTrue();
  }

  private static Stream<Arguments> shouldNotCollectReferencesForProvider() {
    return Stream.of(
        // desc, className
        Arguments.of("Java SDK class", "java.util.ArrayList"),
        Arguments.of("javaagent-tooling class", "io.opentelemetry.javaagent.tooling.Constants"),
        Arguments.of(
            "instrumentation-api class",
            "io.opentelemetry.instrumentation.api.instrumenter.Instrumenter"),
        Arguments.of(
            "bootstrap class", "io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge"));
  }

  @ParameterizedTest(name = "should not collect references for {0}")
  @MethodSource("shouldNotCollectReferencesForProvider")
  void shouldNotCollectReferencesFor(String desc, String className) {
    Predicate<String> alwaysFalsePredicate = name -> false;
    HelperClassPredicate predicate = new HelperClassPredicate(alwaysFalsePredicate);

    assertThat(predicate.isHelperClass(className)).isFalse();
  }
}
