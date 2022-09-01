/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpStatusConverter.CLIENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import io.opentelemetry.api.trace.StatusCode;
import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

public class HttpClientStatusConverterTest {

  @TestFactory
  Collection<DynamicTest> httpStatusCodeToOtelStatus() {
    return Arrays.asList(
        test(100, StatusCode.UNSET),
        test(101, StatusCode.UNSET),
        test(102, StatusCode.UNSET),
        test(103, StatusCode.UNSET),
        test(200, StatusCode.UNSET),
        test(201, StatusCode.UNSET),
        test(202, StatusCode.UNSET),
        test(203, StatusCode.UNSET),
        test(204, StatusCode.UNSET),
        test(205, StatusCode.UNSET),
        test(206, StatusCode.UNSET),
        test(207, StatusCode.UNSET),
        test(208, StatusCode.UNSET),
        test(226, StatusCode.UNSET),
        test(300, StatusCode.UNSET),
        test(301, StatusCode.UNSET),
        test(302, StatusCode.UNSET),
        test(303, StatusCode.UNSET),
        test(304, StatusCode.UNSET),
        test(305, StatusCode.UNSET),
        test(306, StatusCode.UNSET),
        test(307, StatusCode.UNSET),
        test(308, StatusCode.UNSET),
        test(400, StatusCode.ERROR),
        test(401, StatusCode.ERROR),
        test(403, StatusCode.ERROR),
        test(404, StatusCode.ERROR),
        test(405, StatusCode.ERROR),
        test(406, StatusCode.ERROR),
        test(407, StatusCode.ERROR),
        test(408, StatusCode.ERROR),
        test(409, StatusCode.ERROR),
        test(410, StatusCode.ERROR),
        test(411, StatusCode.ERROR),
        test(412, StatusCode.ERROR),
        test(413, StatusCode.ERROR),
        test(414, StatusCode.ERROR),
        test(415, StatusCode.ERROR),
        test(416, StatusCode.ERROR),
        test(417, StatusCode.ERROR),
        test(418, StatusCode.ERROR),
        test(421, StatusCode.ERROR),
        test(422, StatusCode.ERROR),
        test(423, StatusCode.ERROR),
        test(424, StatusCode.ERROR),
        test(425, StatusCode.ERROR),
        test(426, StatusCode.ERROR),
        test(428, StatusCode.ERROR),
        test(429, StatusCode.ERROR),
        test(431, StatusCode.ERROR),
        test(451, StatusCode.ERROR),
        test(500, StatusCode.ERROR),
        test(501, StatusCode.ERROR),
        test(502, StatusCode.ERROR),
        test(503, StatusCode.ERROR),
        test(504, StatusCode.ERROR),
        test(505, StatusCode.ERROR),
        test(506, StatusCode.ERROR),
        test(507, StatusCode.ERROR),
        test(508, StatusCode.ERROR),
        test(510, StatusCode.ERROR),
        test(511, StatusCode.ERROR),

        // Don't exist
        test(99, StatusCode.ERROR),
        test(600, StatusCode.ERROR));
  }

  DynamicTest test(int numeric, StatusCode code) {
    return dynamicTest(
        "" + numeric + " -> " + code,
        () -> assertEquals(code, CLIENT.statusFromHttpStatus(numeric)));
  }
}
