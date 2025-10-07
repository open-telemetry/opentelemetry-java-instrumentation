/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

class MethodsConfigurationParserTest {

  @ParameterizedTest
  @MethodSource("configurationTestData")
  void testConfiguration(String value, Map<String, Set<String>> expected) {
    Map<String, Set<String>> result = MethodsConfigurationParser.parse(value);

    assertThat(result).isEqualTo(expected);
  }

  static Stream<Arguments> configurationTestData() {
    return Stream.of(
        Arguments.of(null, emptyMap()),
        Arguments.of(" ", emptyMap()),
        Arguments.of("some.package.ClassName",
            Collections.<String, Set<String>>singletonMap("some.package.ClassName",
                Collections.emptySet())),
        Arguments.of("some.package.ClassName[ , ]", emptyMap()),
        Arguments.of("some.package.ClassName[ , method]", emptyMap()),
        Arguments.of("some.package.Class$Name[ method , ]",
            Collections.singletonMap("some.package.Class$Name", createSet("method"))),
        Arguments.of("ClassName[ method1,]",
            Collections.singletonMap("ClassName", createSet("method1"))),
        Arguments.of("ClassName[method1 , method2]",
            Collections.singletonMap("ClassName", createSet("method1", "method2"))),
        Arguments.of("Class$1[method1 ] ; Class$2[ method2];",
            createTwoEntryMap("Class$1", createSet("method1"), "Class$2", createSet("method2"))),
        Arguments.of("Duplicate[method1] ; Duplicate[method2]  ;Duplicate[method3];",
            Collections.singletonMap("Duplicate", createSet("method3")))
    );
  }

  private static Map<String, Set<String>> createTwoEntryMap(String key1, Set<String> value1,
      String key2, Set<String> value2) {
    Map<String, Set<String>> map = new HashMap<>();
    map.put(key1, value1);
    map.put(key2, value2);
    return map;
  }

  private static Set<String> createSet(String... elements) {
    Set<String> set = new HashSet<>();
    for (String element : elements) {
      set.add(element);
    }
    return set;
  }
}
