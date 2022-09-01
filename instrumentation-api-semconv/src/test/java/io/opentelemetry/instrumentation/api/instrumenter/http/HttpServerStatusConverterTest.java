package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpStatusConverter.SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import io.opentelemetry.api.trace.StatusCode;
import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

public class HttpServerStatusConverterTest {

  @TestFactory
  Collection<DynamicTest> serverStatuCodes() {
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

        test(400, StatusCode.UNSET),
        test(401, StatusCode.UNSET),
        test(403, StatusCode.UNSET),
        test(404, StatusCode.UNSET),
        test(405, StatusCode.UNSET),
        test(406, StatusCode.UNSET),
        test(407, StatusCode.UNSET),
        test(408, StatusCode.UNSET),
        test(409, StatusCode.UNSET),
        test(410, StatusCode.UNSET),
        test(411, StatusCode.UNSET),
        test(412, StatusCode.UNSET),
        test(413, StatusCode.UNSET),
        test(414, StatusCode.UNSET),
        test(415, StatusCode.UNSET),
        test(416, StatusCode.UNSET),
        test(417, StatusCode.UNSET),
        test(418, StatusCode.UNSET),
        test(421, StatusCode.UNSET),
        test(422, StatusCode.UNSET),
        test(423, StatusCode.UNSET),
        test(424, StatusCode.UNSET),
        test(425, StatusCode.UNSET),
        test(426, StatusCode.UNSET),
        test(428, StatusCode.UNSET),
        test(429, StatusCode.UNSET),
        test(431, StatusCode.UNSET),
        test(451, StatusCode.UNSET),

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
        test(99 , StatusCode.ERROR),
        test(600, StatusCode.ERROR)
    );
  }

  DynamicTest test(int numeric, StatusCode code){
    return dynamicTest("" + numeric + " -> " + code,
        () -> assertEquals(code, SERVER.statusFromHttpStatus(numeric)));
  }


}
