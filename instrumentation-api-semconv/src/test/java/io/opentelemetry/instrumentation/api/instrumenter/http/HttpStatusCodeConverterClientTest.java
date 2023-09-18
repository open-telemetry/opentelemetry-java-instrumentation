/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpStatusCodeConverter.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.trace.StatusCode;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;

class HttpStatusCodeConverterClientTest {

  @ParameterizedTest
  @MethodSource("spanStatusCodes")
  void httpStatusCodeToOtelStatus(int numeric, StatusCode code) {
    assertEquals(code, CLIENT.getSpanStatus(numeric));
  }

  static Stream<Arguments> spanStatusCodes() {
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
        Arguments.of(400, StatusCode.ERROR),
        Arguments.of(401, StatusCode.ERROR),
        Arguments.of(403, StatusCode.ERROR),
        Arguments.of(404, StatusCode.ERROR),
        Arguments.of(405, StatusCode.ERROR),
        Arguments.of(406, StatusCode.ERROR),
        Arguments.of(407, StatusCode.ERROR),
        Arguments.of(408, StatusCode.ERROR),
        Arguments.of(409, StatusCode.ERROR),
        Arguments.of(410, StatusCode.ERROR),
        Arguments.of(411, StatusCode.ERROR),
        Arguments.of(412, StatusCode.ERROR),
        Arguments.of(413, StatusCode.ERROR),
        Arguments.of(414, StatusCode.ERROR),
        Arguments.of(415, StatusCode.ERROR),
        Arguments.of(416, StatusCode.ERROR),
        Arguments.of(417, StatusCode.ERROR),
        Arguments.of(418, StatusCode.ERROR),
        Arguments.of(421, StatusCode.ERROR),
        Arguments.of(422, StatusCode.ERROR),
        Arguments.of(423, StatusCode.ERROR),
        Arguments.of(424, StatusCode.ERROR),
        Arguments.of(425, StatusCode.ERROR),
        Arguments.of(426, StatusCode.ERROR),
        Arguments.of(428, StatusCode.ERROR),
        Arguments.of(429, StatusCode.ERROR),
        Arguments.of(431, StatusCode.ERROR),
        Arguments.of(451, StatusCode.ERROR),
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

  // using the provider from the server test is intentional
  @ArgumentsSource(ClientErrorStatusCodes.class)
  @ArgumentsSource(HttpStatusCodeConverterServerTest.ServerErrorStatusCodes.class)
  @ParameterizedTest
  void shouldReturnErrorType(int httpStatusCode, String errorType) {
    assertThat(CLIENT.getErrorType(httpStatusCode)).isEqualTo(errorType);
  }

  static class ClientErrorStatusCodes implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(
          arguments(400, "Bad Request"),
          arguments(401, "Unauthorized"),
          arguments(402, "Payment Required"),
          arguments(403, "Forbidden"),
          arguments(404, "Not Found"),
          arguments(405, "Method Not Allowed"),
          arguments(406, "Not Acceptable"),
          arguments(407, "Proxy Authentication Required"),
          arguments(408, "Request Timeout"),
          arguments(409, "Conflict"),
          arguments(410, "Gone"),
          arguments(411, "Length Required"),
          arguments(412, "Precondition Failed"),
          arguments(413, "Content Too Large"),
          arguments(414, "URI Too Long"),
          arguments(415, "Unsupported Media Type"),
          arguments(416, "Range Not Satisfiable"),
          arguments(417, "Expectation Failed"),
          arguments(418, "I'm a teapot"),
          arguments(421, "Misdirected Request"),
          arguments(422, "Unprocessable Content"),
          arguments(423, "Locked"),
          arguments(424, "Failed Dependency"),
          arguments(425, "Too Early"),
          arguments(426, "Upgrade Required"),
          arguments(428, "Precondition Required"),
          arguments(429, "Too Many Requests"),
          arguments(431, "Request Header Fields Too Large"),
          arguments(451, "Unavailable For Legal Reasons"));
    }
  }

  @ArgumentsSource(OtherStatusCodes.class)
  @ParameterizedTest
  void noErrorTypeForOtherStatusCodes(int httpStatusCode) {
    assertThat(CLIENT.getErrorType(httpStatusCode)).isNull();
  }

  static class OtherStatusCodes implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext)
        throws Exception {

      Set<Integer> errorCodes =
          Stream.concat(
                  new HttpStatusCodeConverterServerTest.ServerErrorStatusCodes()
                      .provideArguments(extensionContext),
                  new ClientErrorStatusCodes().provideArguments(extensionContext))
              .map(args -> args.get()[0])
              .map(Integer.class::cast)
              .collect(Collectors.toSet());

      return IntStream.range(100, 600)
          .filter(statusCode -> !errorCodes.contains(statusCode))
          .mapToObj(Arguments::of);
    }
  }
}
