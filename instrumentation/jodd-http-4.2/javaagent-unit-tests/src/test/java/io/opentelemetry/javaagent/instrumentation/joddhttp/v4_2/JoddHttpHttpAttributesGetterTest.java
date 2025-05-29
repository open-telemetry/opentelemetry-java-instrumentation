/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2;

import static jodd.http.HttpStatus.HTTP_FORBIDDEN;
import static jodd.http.HttpStatus.HTTP_INTERNAL_ERROR;
import static jodd.http.HttpStatus.HTTP_NOT_FOUND;
import static jodd.http.HttpStatus.HTTP_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import org.junit.jupiter.api.Test;

class JoddHttpHttpAttributesGetterTest {

  private static final JoddHttpHttpAttributesGetter attributesGetter =
      new JoddHttpHttpAttributesGetter();

  @Test
  void getMethod() {
    for (String method : Arrays.asList("GET", "PUT", "POST", "PATCH")) {
      assertEquals(method, attributesGetter.getHttpRequestMethod(new HttpRequest().method(method)));
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
    assertEquals(
        "http://test.com/test/subpath?param1=val1&param2=val1&param2=val2",
        attributesGetter.getUrlFull(request));
  }

  @Test
  void getHttpRequestHeader() {
    HttpRequest request =
        HttpRequest.get("/test")
            .header("single", "val1")
            .header("multiple", "val1")
            .header("multiple", "val2");
    List<String> headerVals = attributesGetter.getHttpRequestHeader(request, "single");
    assertEquals(1, headerVals.size());
    assertEquals("val1", headerVals.get(0));
    headerVals = attributesGetter.getHttpRequestHeader(request, "multiple");
    assertEquals(2, headerVals.size());
    assertEquals("val1", headerVals.get(0));
    assertEquals("val2", headerVals.get(1));
    headerVals = attributesGetter.getHttpRequestHeader(request, "not-existing");
    assertEquals(0, headerVals.size());
  }

  @Test
  void getStatusCode() {
    for (Integer code :
        Arrays.asList(HTTP_OK, HTTP_FORBIDDEN, HTTP_INTERNAL_ERROR, HTTP_NOT_FOUND)) {
      assertEquals(
          code,
          attributesGetter.getHttpResponseStatusCode(
              null, new HttpResponse().statusCode(code), null));
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
    assertEquals(1, headerVals.size());
    assertEquals("val1", headerVals.get(0));
    headerVals = attributesGetter.getHttpResponseHeader(null, response, "multiple");
    assertEquals(2, headerVals.size());
    assertEquals("val1", headerVals.get(0));
    assertEquals("val2", headerVals.get(1));
    headerVals = attributesGetter.getHttpResponseHeader(null, response, "not-existing");
    assertEquals(0, headerVals.size());
  }
}
