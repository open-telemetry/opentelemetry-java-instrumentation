/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class InstrumentationNameComparatorTest {

  @Test
  void testComparesLibrariesDifferentName() {
    InstrumentationModule moduleB = new InstrumentationModule.Builder("b-module").build();
    InstrumentationModule moduleC = new InstrumentationModule.Builder("c-module").build();
    InstrumentationModule moduleA = new InstrumentationModule.Builder("a-module").build();

    List<String> modules =
        Stream.of(moduleB, moduleC, moduleA)
            .sorted(InstrumentationNameComparator.BY_NAME_AND_VERSION)
            .map(InstrumentationModule::getInstrumentationName)
            .toList();
    assertThat(modules).containsExactly("a-module", "b-module", "c-module");
  }

  @Test
  void testComparesLibrariesDifferentVersions() {
    InstrumentationModule moduleB = new InstrumentationModule.Builder("b-module").build();
    InstrumentationModule moduleB2 = new InstrumentationModule.Builder("b-module-2.0").build();
    InstrumentationModule moduleB4 = new InstrumentationModule.Builder("b-module-4.2").build();

    InstrumentationModule moduleB25 = new InstrumentationModule.Builder("b-module-2.5").build();

    List<String> modules =
        Stream.of(moduleB4, moduleB25, moduleB, moduleB2)
            .sorted(InstrumentationNameComparator.BY_NAME_AND_VERSION)
            .map(InstrumentationModule::getInstrumentationName)
            .toList();

    assertThat(modules).containsExactly("b-module", "b-module-2.0", "b-module-2.5", "b-module-4.2");
  }

  @Test
  void testComparesMultiDigitMinorVersions() {
    InstrumentationModule module19 = new InstrumentationModule.Builder("lib-1.9").build();
    InstrumentationModule module110 = new InstrumentationModule.Builder("lib-1.10").build();
    InstrumentationModule module156 = new InstrumentationModule.Builder("lib-1.56").build();
    InstrumentationModule module157 = new InstrumentationModule.Builder("lib-1.57").build();
    InstrumentationModule module20 = new InstrumentationModule.Builder("lib-2.0").build();

    List<String> modules =
        Stream.of(module157, module110, module156, module19, module20)
            .sorted(InstrumentationNameComparator.BY_NAME_AND_VERSION)
            .map(InstrumentationModule::getInstrumentationName)
            .toList();

    assertThat(modules).containsExactly("lib-1.9", "lib-1.10", "lib-1.56", "lib-1.57", "lib-2.0");
  }

  @Test
  void testComparesPatchVersions() {
    // Test that 3.1 comes before 3.1.6, which comes before 3.2
    InstrumentationModule module31 = new InstrumentationModule.Builder("couchbase-3.1").build();
    InstrumentationModule module316 = new InstrumentationModule.Builder("couchbase-3.1.6").build();
    InstrumentationModule module32 = new InstrumentationModule.Builder("couchbase-3.2").build();
    InstrumentationModule module34 = new InstrumentationModule.Builder("couchbase-3.4").build();

    List<String> modules =
        Stream.of(module34, module316, module32, module31)
            .sorted(InstrumentationNameComparator.BY_NAME_AND_VERSION)
            .map(InstrumentationModule::getInstrumentationName)
            .toList();

    assertThat(modules)
        .containsExactly("couchbase-3.1", "couchbase-3.1.6", "couchbase-3.2", "couchbase-3.4");
  }

  @Test
  void testComparesVersionedAndUnversioned() {
    InstrumentationModule moduleUnversioned = new InstrumentationModule.Builder("lib").build();
    InstrumentationModule module10 = new InstrumentationModule.Builder("lib-1.0").build();
    InstrumentationModule module20 = new InstrumentationModule.Builder("lib-2.0").build();

    List<String> modules =
        Stream.of(module20, module10, moduleUnversioned)
            .sorted(InstrumentationNameComparator.BY_NAME_AND_VERSION)
            .map(InstrumentationModule::getInstrumentationName)
            .toList();

    assertThat(modules).containsExactly("lib", "lib-1.0", "lib-2.0");
  }
}
