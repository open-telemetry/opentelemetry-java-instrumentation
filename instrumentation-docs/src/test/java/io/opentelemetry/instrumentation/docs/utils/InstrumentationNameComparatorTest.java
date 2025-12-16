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
    InstrumentationModule moduleB =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/b-module")
            .instrumentationName("b-module")
            .namespace("b-module")
            .group("b-module")
            .build();
    InstrumentationModule moduleC =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/c-module")
            .instrumentationName("c-module")
            .namespace("c-module")
            .group("c-module")
            .build();
    InstrumentationModule moduleA =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/a-module")
            .instrumentationName("a-module")
            .namespace("a-module")
            .group("a-module")
            .build();

    List<String> modules =
        Stream.of(moduleB, moduleC, moduleA)
            .sorted(InstrumentationNameComparator.BY_NAME_AND_VERSION)
            .map(InstrumentationModule::getInstrumentationName)
            .toList();
    assertThat(modules).containsExactly("a-module", "b-module", "c-module");
  }

  @Test
  void testComparesLibrariesDifferentVersions() {
    InstrumentationModule moduleB =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/b-module")
            .instrumentationName("b-module")
            .namespace("b-module")
            .group("b-module")
            .build();
    InstrumentationModule moduleB2 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/b-module-2.0")
            .instrumentationName("b-module-2.0")
            .namespace("b-module")
            .group("b-module")
            .build();
    InstrumentationModule moduleB4 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/b-module-4.2")
            .instrumentationName("b-module-4.2")
            .namespace("b-module")
            .group("b-module")
            .build();

    InstrumentationModule moduleB25 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/b-module-2.5")
            .instrumentationName("b-module-2.5")
            .namespace("b-module")
            .group("b-module")
            .build();

    List<String> modules =
        Stream.of(moduleB4, moduleB25, moduleB, moduleB2)
            .sorted(InstrumentationNameComparator.BY_NAME_AND_VERSION)
            .map(InstrumentationModule::getInstrumentationName)
            .toList();

    assertThat(modules).containsExactly("b-module", "b-module-2.0", "b-module-2.5", "b-module-4.2");
  }

  @Test
  void testComparesMultiDigitMinorVersions() {
    InstrumentationModule module19 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/lib/lib-1.9")
            .instrumentationName("lib-1.9")
            .namespace("lib")
            .group("lib")
            .build();
    InstrumentationModule module110 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/lib/lib-1.10")
            .instrumentationName("lib-1.10")
            .namespace("lib")
            .group("lib")
            .build();
    InstrumentationModule module156 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/lib/lib-1.56")
            .instrumentationName("lib-1.56")
            .namespace("lib")
            .group("lib")
            .build();
    InstrumentationModule module157 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/lib/lib-1.57")
            .instrumentationName("lib-1.57")
            .namespace("lib")
            .group("lib")
            .build();
    InstrumentationModule module20 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/lib/lib-2.0")
            .instrumentationName("lib-2.0")
            .namespace("lib")
            .group("lib")
            .build();

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
    InstrumentationModule module31 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/couchbase/couchbase-3.1")
            .instrumentationName("couchbase-3.1")
            .namespace("couchbase")
            .group("couchbase")
            .build();
    InstrumentationModule module316 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/couchbase/couchbase-3.1.6")
            .instrumentationName("couchbase-3.1.6")
            .namespace("couchbase")
            .group("couchbase")
            .build();
    InstrumentationModule module32 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/couchbase/couchbase-3.2")
            .instrumentationName("couchbase-3.2")
            .namespace("couchbase")
            .group("couchbase")
            .build();
    InstrumentationModule module34 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/couchbase/couchbase-3.4")
            .instrumentationName("couchbase-3.4")
            .namespace("couchbase")
            .group("couchbase")
            .build();

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
    InstrumentationModule moduleUnversioned =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/lib/lib")
            .instrumentationName("lib")
            .namespace("lib")
            .group("lib")
            .build();
    InstrumentationModule module10 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/lib/lib-1.0")
            .instrumentationName("lib-1.0")
            .namespace("lib")
            .group("lib")
            .build();
    InstrumentationModule module20 =
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/lib/lib-2.0")
            .instrumentationName("lib-2.0")
            .namespace("lib")
            .group("lib")
            .build();

    List<String> modules =
        Stream.of(module20, module10, moduleUnversioned)
            .sorted(InstrumentationNameComparator.BY_NAME_AND_VERSION)
            .map(InstrumentationModule::getInstrumentationName)
            .toList();

    assertThat(modules).containsExactly("lib", "lib-1.0", "lib-2.0");
  }
}
