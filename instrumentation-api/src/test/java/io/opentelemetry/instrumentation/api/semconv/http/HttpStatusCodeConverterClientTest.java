/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static io.opentelemetry.instrumentation.api.semconv.http.HttpStatusCodeConverter.CLIENT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HttpStatusCodeConverterClientTest {

  @ParameterizedTest
  @MethodSource("spanStatusCodes")
  void httpStatusCodeToOtelStatus(int numeric, boolean isError) {
    assertEquals(isError, CLIENT.isError(numeric));
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
        Arguments.of(400, true),
        Arguments.of(401, true),
        Arguments.of(403, true),
        Arguments.of(404, true),
        Arguments.of(405, true),
        Arguments.of(406, true),
        Arguments.of(407, true),
        Arguments.of(408, true),
        Arguments.of(409, true),
        Arguments.of(410, true),
        Arguments.of(411, true),
        Arguments.of(412, true),
        Arguments.of(413, true),
        Arguments.of(414, true),
        Arguments.of(415, true),
        Arguments.of(416, true),
        Arguments.of(417, true),
        Arguments.of(418, true),
        Arguments.of(421, true),
        Arguments.of(422, true),
        Arguments.of(423, true),
        Arguments.of(424, true),
        Arguments.of(425, true),
        Arguments.of(426, true),
        Arguments.of(428, true),
        Arguments.of(429, true),
        Arguments.of(431, true),
        Arguments.of(451, true),
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
