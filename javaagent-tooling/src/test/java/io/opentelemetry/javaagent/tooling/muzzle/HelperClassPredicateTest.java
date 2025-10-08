/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Predicate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HelperClassPredicateTest {

  @ParameterizedTest(name = "should collect references for {0}")
  @CsvSource({
    "javaagent instrumentation class, io.opentelemetry.javaagent.instrumentation.some_instrumentation.Advice",
    "library instrumentation class, io.opentelemetry.instrumentation.LibraryClass",
    "additional library instrumentation class, com.example.instrumentation.library.ThirdPartyExternalInstrumentation"
  })
  void shouldCollectReferencesFor(String desc, String className) {
    Predicate<String> additionalLibraryPredicate =
        name -> name.startsWith("com.example.instrumentation.library");
    HelperClassPredicate predicate = new HelperClassPredicate(additionalLibraryPredicate);

    assertThat(predicate.isHelperClass(className)).isTrue();
  }

  @ParameterizedTest(name = "should not collect references for {0}")
  @CsvSource({
    "Java SDK class, java.util.ArrayList",
    "javaagent-tooling class, io.opentelemetry.javaagent.tooling.Constants",
    "instrumentation-api class, io.opentelemetry.instrumentation.api.InstrumentationVersion",
    "bootstrap class, io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge"
  })
  void shouldNotCollectReferencesFor(String desc, String className) {
    Predicate<String> alwaysFalsePredicate = name -> false;
    HelperClassPredicate predicate = new HelperClassPredicate(alwaysFalsePredicate);

    assertThat(predicate.isHelperClass(className)).isFalse();
  }
}
