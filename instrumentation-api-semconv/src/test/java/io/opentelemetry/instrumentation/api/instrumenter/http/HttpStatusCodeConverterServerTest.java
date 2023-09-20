/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpStatusCodeConverter.SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HttpStatusCodeConverterServerTest {

  @ParameterizedTest
  @MethodSource("spanStatusCodes")
  void httpStatusCodeToOtelStatus(int numeric, boolean isError) {
    assertEquals(isError, SERVER.isError(numeric));
  }

  static Stream<Arguments> spanStatusCodes() {
    return Stream.of(
        Arguments.of(100, false),
        Arguments.of(101, false),
        Arguments.of(102, false),
        Arguments.of(103, false),
        Arguments.of(200, false),
        Arguments.of(201, false),
        Arguments.of(202, false),
        Arguments.of(203, false),
        Arguments.of(204, false),
        Arguments.of(205, false),
        Arguments.of(206, false),
        Arguments.of(207, false),
        Arguments.of(208, false),
        Arguments.of(226, false),
        Arguments.of(300, false),
        Arguments.of(301, false),
        Arguments.of(302, false),
        Arguments.of(303, false),
        Arguments.of(304, false),
        Arguments.of(305, false),
        Arguments.of(306, false),
        Arguments.of(307, false),
        Arguments.of(308, false),
        Arguments.of(400, false),
        Arguments.of(401, false),
        Arguments.of(403, false),
        Arguments.of(404, false),
        Arguments.of(405, false),
        Arguments.of(406, false),
        Arguments.of(407, false),
        Arguments.of(408, false),
        Arguments.of(409, false),
        Arguments.of(410, false),
        Arguments.of(411, false),
        Arguments.of(412, false),
        Arguments.of(413, false),
        Arguments.of(414, false),
        Arguments.of(415, false),
        Arguments.of(416, false),
        Arguments.of(417, false),
        Arguments.of(418, false),
        Arguments.of(421, false),
        Arguments.of(422, false),
        Arguments.of(423, false),
        Arguments.of(424, false),
        Arguments.of(425, false),
        Arguments.of(426, false),
        Arguments.of(428, false),
        Arguments.of(429, false),
        Arguments.of(431, false),
        Arguments.of(451, false),
        Arguments.of(500, true),
        Arguments.of(501, true),
        Arguments.of(502, true),
        Arguments.of(503, true),
        Arguments.of(504, true),
        Arguments.of(505, true),
        Arguments.of(506, true),
        Arguments.of(507, true),
        Arguments.of(508, true),
        Arguments.of(510, true),
        Arguments.of(511, true),

        // Don't exist
        Arguments.of(99, true),
        Arguments.of(600, true));
  }
}
