/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.contrib.http.core;

import static com.google.common.truth.Truth.assertThat;

import io.opentelemetry.trace.Status;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Unit tests for {@link StatusCodeConverter}. */
@RunWith(value = Parameterized.class)
public class StatusCodeConverterTest {

  private static final Logger LOGGER = Logger.getLogger(StatusCodeConverterTest.class.getName());

  /**
   * Provides parameterized data.
   *
   * @return the data
   */
  @Parameters
  public static Collection<Object[]> data() {
    // List from https://developer.mozilla.org/en-US/docs/Web/HTTP
    return Arrays.asList(
        new Object[][] {
          {100, "Continue", Status.UNKNOWN},
          {101, "Switching Protocols", Status.UNKNOWN},
          {103, "Early Hints", Status.UNKNOWN},
          {200, "OK", Status.OK},
          {201, "Created", Status.OK},
          {202, "Accepted", Status.OK},
          {203, "Non-Authoritative Information", Status.OK},
          {204, "No Content", Status.OK},
          {205, "Reset Content", Status.OK},
          {206, "Partial Content", Status.OK},
          {300, "Multiple Choices", Status.OK},
          {301, "Moved Permenantly", Status.OK},
          {302, "Found", Status.OK},
          {303, "See Other", Status.OK},
          {304, "Not Modified", Status.OK},
          {307, "Temporary Redirect", Status.OK},
          {308, "Permanent Redirect", Status.OK},
          {400, "Bad Request", Status.INVALID_ARGUMENT},
          {401, "Unauthorized", Status.UNAUTHENTICATED},
          {402, "Payment Required", Status.INVALID_ARGUMENT},
          {403, "Forbidden", Status.PERMISSION_DENIED},
          {404, "Not Found", Status.NOT_FOUND},
          {405, "Method Not Allowed", Status.INVALID_ARGUMENT},
          {406, "Not Acceptable", Status.INVALID_ARGUMENT},
          {407, "Proxy Authentication Required", Status.INVALID_ARGUMENT},
          {408, "Request Timeout", Status.INVALID_ARGUMENT},
          {409, "Conflict", Status.INVALID_ARGUMENT},
          {410, "Gone", Status.INVALID_ARGUMENT},
          {411, "Length Required", Status.INVALID_ARGUMENT},
          {412, "Precondition Failed", Status.INVALID_ARGUMENT},
          {413, "Payload Too Large", Status.INVALID_ARGUMENT},
          {414, "URI Too Long", Status.INVALID_ARGUMENT},
          {415, "Unsupported Media Type", Status.INVALID_ARGUMENT},
          {416, "Range Not Satisfiable", Status.INVALID_ARGUMENT},
          {417, "Expectation Failed", Status.INVALID_ARGUMENT},
          {418, "I'm a teapot", Status.INVALID_ARGUMENT},
          {422, "Unprocessable Entity", Status.INVALID_ARGUMENT},
          {425, "Too Early", Status.INVALID_ARGUMENT},
          {426, "Upgrade Required", Status.INVALID_ARGUMENT},
          {428, "Precondition Required", Status.INVALID_ARGUMENT},
          {429, "Too Many Requests", Status.RESOURCE_EXHAUSTED},
          {431, "Request Header Fields Too Large", Status.INVALID_ARGUMENT},
          {451, "Unavailable For Legal Reasons", Status.INVALID_ARGUMENT},
          {500, "Internal Server Error", Status.INTERNAL},
          {501, "Not Implemented", Status.UNIMPLEMENTED},
          {502, "Bad Gateway", Status.INTERNAL},
          {503, "Service Unavailable", Status.UNAVAILABLE},
          {504, "Gateway Timeout", Status.DEADLINE_EXCEEDED},
          {505, "HTTP Version Not Supported", Status.INTERNAL},
          {511, "Network Authentication Required", Status.INTERNAL},
        });
  }

  private final StatusCodeConverter converter = new StatusCodeConverter();
  private final int source;
  private final String description;
  private final Status target;

  /**
   * Constructs test.
   *
   * @param source the http status code
   * @param description the http status description
   * @param target the equivalent OT status
   */
  public StatusCodeConverterTest(int source, String description, Status target) {
    this.source = source;
    this.description = description;
    this.target = target;
  }

  @Test
  public void shouldConvertAllStandardHttpStatusCodesCorrectly() {
    LOGGER.log(Level.FINER, "testing " + description);
    assertThat(converter.convert(source)).isEqualTo(target);
  }
}
