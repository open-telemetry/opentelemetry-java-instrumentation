/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InstrumentationNameComparatorTest {

  private static Stream<Arguments> sortCases() {
    return Stream.of(
        argumentSet(
            "different names",
            List.of("b-module", "c-module", "a-module"),
            List.of("a-module", "b-module", "c-module")),
        argumentSet(
            "different versions",
            List.of("b-module-4.2", "b-module-2.5", "b-module", "b-module-2.0"),
            List.of("b-module", "b-module-2.0", "b-module-2.5", "b-module-4.2")),
        argumentSet(
            "multi-digit minor versions",
            List.of("lib-1.57", "lib-1.10", "lib-1.56", "lib-1.9", "lib-2.0"),
            List.of("lib-1.9", "lib-1.10", "lib-1.56", "lib-1.57", "lib-2.0")),
        argumentSet(
            // 3.1 comes before 3.1.6, which comes before 3.2
            "patch versions",
            List.of("couchbase-3.4", "couchbase-3.1.6", "couchbase-3.2", "couchbase-3.1"),
            List.of("couchbase-3.1", "couchbase-3.1.6", "couchbase-3.2", "couchbase-3.4")),
        argumentSet(
            "versioned and unversioned",
            List.of("lib-2.0", "lib-1.0", "lib"),
            List.of("lib", "lib-1.0", "lib-2.0")));
  }

  @ParameterizedTest
  @MethodSource("sortCases")
  void sortsModuleNames(List<String> input, List<String> expected) {
    List<String> sorted =
        input.stream()
            .map(moduleName -> new InstrumentationModule.Builder(moduleName).build())
            .sorted(InstrumentationNameComparator.BY_NAME_AND_VERSION)
            .map(InstrumentationModule::getInstrumentationName)
            .toList();
    assertThat(sorted).containsExactlyElementsOf(expected);
  }
}
