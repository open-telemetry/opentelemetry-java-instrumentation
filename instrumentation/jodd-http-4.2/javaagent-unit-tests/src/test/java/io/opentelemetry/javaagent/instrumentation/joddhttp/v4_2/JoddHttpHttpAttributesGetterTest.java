/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2;

import static java.util.Arrays.asList;
import static jodd.http.HttpStatus.HTTP_FORBIDDEN;
import static jodd.http.HttpStatus.HTTP_INTERNAL_ERROR;
import static jodd.http.HttpStatus.HTTP_NOT_FOUND;
import static jodd.http.HttpStatus.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import org.junit.jupiter.api.Test;

class JoddHttpHttpAttributesGetterTest {

  private static final JoddHttpHttpAttributesGetter attributesGetter =
      new JoddHttpHttpAttributesGetter();

  @Test
  void getMethod() {
    for (String method : asList("GET", "PUT", "POST", "PATCH")) {
      assertThat(attributesGetter.getHttpRequestMethod(new HttpRequest().method(method)))
          .isEqualTo(method);
    }
  }

  @Test
  void getUrl() {
    HttpRequest request =
        HttpRequest.get("/test/subpath")
            .host("test.com")
            .query("param1", "val1")
            .query("param2", "val1")
            .query("param2", "val2");
    assertThat(attributesGetter.getUrlFull(request))
        .isEqualTo("http://test.com/test/subpath?param1=val1&param2=val1&param2=val2");
  }

  @Test
  void getHttpRequestHeader() {
    HttpRequest request =
        HttpRequest.get("/test")
            .header("single", "val1")
            .header("multiple", "val1")
            .header("multiple", "val2");
    List<String> headerVals = attributesGetter.getHttpRequestHeader(request, "single");
    assertThat(headerVals.size()).isEqualTo(1);
    assertThat(headerVals.get(0)).isEqualTo("val1");
    headerVals = attributesGetter.getHttpRequestHeader(request, "multiple");
    assertThat(headerVals.size()).isEqualTo(2);
    assertThat(headerVals.get(0)).isEqualTo("val1");
    assertThat(headerVals.get(1)).isEqualTo("val2");
    headerVals = attributesGetter.getHttpRequestHeader(request, "not-existing");
    assertThat(headerVals.size()).isEqualTo(0);
  }

  @Test
  void getStatusCode() {
    for (Integer code : asList(HTTP_OK, HTTP_FORBIDDEN, HTTP_INTERNAL_ERROR, HTTP_NOT_FOUND)) {
      assertThat(
              attributesGetter.getHttpResponseStatusCode(
                  null, new HttpResponse().statusCode(code), null))
          .isEqualTo(code);
    }
  }

  @Test
  void getResponseHeader() {
    HttpResponse response =
        new HttpResponse()
            .header("single", "val1")
            .header("multiple", "val1")
            .header("multiple", "val2");
    List<String> headerVals = attributesGetter.getHttpResponseHeader(null, response, "single");
    assertThat(headerVals.size()).isEqualTo(1);
    assertThat(headerVals.get(0)).isEqualTo("val1");
    headerVals = attributesGetter.getHttpResponseHeader(null, response, "multiple");
    assertThat(headerVals.size()).isEqualTo(2);
    assertThat(headerVals.get(0)).isEqualTo("val1");
    assertThat(headerVals.get(1)).isEqualTo("val2");
    headerVals = attributesGetter.getHttpResponseHeader(null, response, "not-existing");
    assertThat(headerVals.size()).isEqualTo(0);
  }
}
