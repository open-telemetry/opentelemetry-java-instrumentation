/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpStatusConverter.SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.trace.StatusCode;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class HttpServerStatusConverterTest {

  @ParameterizedTest
  @MethodSource("generateParams")
  void httpStatusCodeToOtelStatus(int numeric, StatusCode code) {
    assertEquals(code, SERVER.statusFromHttpStatus(numeric));
  }

  static Stream<Arguments> generateParams() {
    return Stream.of(
        Arguments.of(100, StatusCode.UNSET),
        Arguments.of(101, StatusCode.UNSET),
        Arguments.of(102, StatusCode.UNSET),
        Arguments.of(103, StatusCode.UNSET),
        Arguments.of(200, StatusCode.UNSET),
        Arguments.of(201, StatusCode.UNSET),
        Arguments.of(202, StatusCode.UNSET),
        Arguments.of(203, StatusCode.UNSET),
        Arguments.of(204, StatusCode.UNSET),
        Arguments.of(205, StatusCode.UNSET),
        Arguments.of(206, StatusCode.UNSET),
        Arguments.of(207, StatusCode.UNSET),
        Arguments.of(208, StatusCode.UNSET),
        Arguments.of(226, StatusCode.UNSET),
        Arguments.of(300, StatusCode.UNSET),
        Arguments.of(301, StatusCode.UNSET),
        Arguments.of(302, StatusCode.UNSET),
        Arguments.of(303, StatusCode.UNSET),
        Arguments.of(304, StatusCode.UNSET),
        Arguments.of(305, StatusCode.UNSET),
        Arguments.of(306, StatusCode.UNSET),
        Arguments.of(307, StatusCode.UNSET),
        Arguments.of(308, StatusCode.UNSET),
        Arguments.of(400, StatusCode.UNSET),
        Arguments.of(401, StatusCode.UNSET),
        Arguments.of(403, StatusCode.UNSET),
        Arguments.of(404, StatusCode.UNSET),
        Arguments.of(405, StatusCode.UNSET),
        Arguments.of(406, StatusCode.UNSET),
        Arguments.of(407, StatusCode.UNSET),
        Arguments.of(408, StatusCode.UNSET),
        Arguments.of(409, StatusCode.UNSET),
        Arguments.of(410, StatusCode.UNSET),
        Arguments.of(411, StatusCode.UNSET),
        Arguments.of(412, StatusCode.UNSET),
        Arguments.of(413, StatusCode.UNSET),
        Arguments.of(414, StatusCode.UNSET),
        Arguments.of(415, StatusCode.UNSET),
        Arguments.of(416, StatusCode.UNSET),
        Arguments.of(417, StatusCode.UNSET),
        Arguments.of(418, StatusCode.UNSET),
        Arguments.of(421, StatusCode.UNSET),
        Arguments.of(422, StatusCode.UNSET),
        Arguments.of(423, StatusCode.UNSET),
        Arguments.of(424, StatusCode.UNSET),
        Arguments.of(425, StatusCode.UNSET),
        Arguments.of(426, StatusCode.UNSET),
        Arguments.of(428, StatusCode.UNSET),
        Arguments.of(429, StatusCode.UNSET),
        Arguments.of(431, StatusCode.UNSET),
        Arguments.of(451, StatusCode.UNSET),
        Arguments.of(500, StatusCode.ERROR),
        Arguments.of(501, StatusCode.ERROR),
        Arguments.of(502, StatusCode.ERROR),
        Arguments.of(503, StatusCode.ERROR),
        Arguments.of(504, StatusCode.ERROR),
        Arguments.of(505, StatusCode.ERROR),
        Arguments.of(506, StatusCode.ERROR),
        Arguments.of(507, StatusCode.ERROR),
        Arguments.of(508, StatusCode.ERROR),
        Arguments.of(510, StatusCode.ERROR),
        Arguments.of(511, StatusCode.ERROR),

        // Don't exist
        Arguments.of(99, StatusCode.ERROR),
        Arguments.of(600, StatusCode.ERROR));
  }
}
