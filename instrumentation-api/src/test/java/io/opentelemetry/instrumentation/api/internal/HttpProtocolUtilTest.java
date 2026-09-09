/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HttpProtocolUtilTest {

  @ParameterizedTest
  @MethodSource("formatVersionArguments")
  void formatVersion(int majorVersion, int minorVersion, String expected) {
    assertThat(HttpProtocolUtil.formatVersion(majorVersion, minorVersion)).isEqualTo(expected);
  }

  private static Stream<Arguments> formatVersionArguments() {
    return Stream.of(
        argumentSet("HTTP 1.0", 1, 0, "1.0"),
        argumentSet("HTTP 1.1", 1, 1, "1.1"),
        argumentSet("HTTP 2", 2, 0, "2"),
        argumentSet("HTTP 3", 3, 0, "3"));
  }
}
