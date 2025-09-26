/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MethodsConfigurationParserTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("testConfigurationProvider")
  void testConfiguration(String testName, String value, Map<String, Set<String>> expected) {
    assertEquals(expected, MethodsConfigurationParser.parse(value));
  }

  private static Stream<Arguments> testConfigurationProvider() {
    return Stream.of(
        Arguments.of("null value", null, emptyMap()),
        Arguments.of("empty string", " ", emptyMap()),
        Arguments.of(
            "simple class name",
            "some.package.ClassName",
            mapOf("some.package.ClassName", emptySet())),
        Arguments.of("class with empty method list", "some.package.ClassName[ , ]", emptyMap()),
        Arguments.of(
            "class with invalid method", "some.package.ClassName[ , method]", emptyMap()),
        Arguments.of(
            "class with single method",
            "some.package.Class$Name[ method , ]",
            mapOf("some.package.Class$Name", singleton("method"))),
        Arguments.of(
            "class with trailing comma",
            "ClassName[ method1,]",
            mapOf("ClassName", singleton("method1"))),
        Arguments.of(
            "class with multiple methods",
            "ClassName[method1 , method2]",
            mapOf("ClassName", setOf("method1", "method2"))),
        Arguments.of(
            "multiple classes",
            "Class$1[method1 ] ; Class$2[ method2];",
            mapOfTwo("Class$1", singleton("method1"), "Class$2", singleton("method2"))),
        Arguments.of(
            "duplicate class names (last wins)",
            "Duplicate[method1] ; Duplicate[method2]  ;Duplicate[method3];",
            mapOf("Duplicate", singleton("method3"))));
  }

  private static Map<String, Set<String>> mapOf(String key, Set<String> value) {
    Map<String, Set<String>> map = new HashMap<>();
    map.put(key, value);
    return map;
  }

  private static Map<String, Set<String>> mapOfTwo(String key1, Set<String> value1, String key2, Set<String> value2) {
    Map<String, Set<String>> map = new HashMap<>();
    map.put(key1, value1);
    map.put(key2, value2);
    return map;
  }

  private static Set<String> setOf(String... values) {
    Set<String> set = new HashSet<>();
    for (String value : values) {
      set.add(value);
    }
    return set;
  }
}