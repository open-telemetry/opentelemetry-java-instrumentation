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
import static org.junit.jupiter.api.Assertions.assertNull;

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HttpFlavorValues;
import java.util.Arrays;
import java.util.List;
import jodd.http.HttpBase;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import org.junit.jupiter.api.Test;

class JoddHttpHttpAttributesGetterTest {

  private static final JoddHttpHttpAttributesGetter attributesGetter =
      new JoddHttpHttpAttributesGetter();

  @Test
  void getMethod() {
    for (String method : Arrays.asList("GET", "PUT", "POST", "PATCH")) {
      assertEquals(method, attributesGetter.getMethod(new HttpRequest().method(method)));
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
        attributesGetter.getUrl(request));
  }

  @Test
  void getRequestHeader() {
    HttpRequest request =
        HttpRequest.get("/test")
            .header("single", "val1")
            .header("multiple", "val1")
            .header("multiple", "val2");
    List<String> headerVals = attributesGetter.getRequestHeader(request, "single");
    assertEquals(1, headerVals.size());
    assertEquals("val1", headerVals.get(0));
    headerVals = attributesGetter.getRequestHeader(request, "multiple");
    assertEquals(2, headerVals.size());
    assertEquals("val1", headerVals.get(0));
    assertEquals("val2", headerVals.get(1));
    headerVals = attributesGetter.getRequestHeader(request, "not-existing");
    assertEquals(0, headerVals.size());
  }

  @Test
  void getStatusCode() {
    for (Integer code :
        Arrays.asList(HTTP_OK, HTTP_FORBIDDEN, HTTP_INTERNAL_ERROR, HTTP_NOT_FOUND)) {
      assertEquals(
          code, attributesGetter.getStatusCode(null, new HttpResponse().statusCode(code), null));
    }
  }

  @Test
  void getFlavor() {
    HttpRequest request = HttpRequest.get("/test").httpVersion(HttpBase.HTTP_1_1);
    assertEquals(HttpFlavorValues.HTTP_1_1, attributesGetter.getFlavor(request, null));
    request.httpVersion(null);
    assertNull(attributesGetter.getFlavor(request, null));
    request.httpVersion("INVALID-HTTP-Version");
    assertNull(attributesGetter.getFlavor(request, null));
    request.httpVersion(null);
    HttpResponse response = new HttpResponse().httpVersion(HttpBase.HTTP_1_0);
    assertEquals(HttpFlavorValues.HTTP_1_0, attributesGetter.getFlavor(request, response));
    response.httpVersion(null);
    assertNull(attributesGetter.getFlavor(request, response));
  }

  @Test
  void getResponseHeader() {
    HttpResponse response =
        new HttpResponse()
            .header("single", "val1")
            .header("multiple", "val1")
            .header("multiple", "val2");
    List<String> headerVals = attributesGetter.getResponseHeader(null, response, "single");
    assertEquals(1, headerVals.size());
    assertEquals("val1", headerVals.get(0));
    headerVals = attributesGetter.getResponseHeader(null, response, "multiple");
    assertEquals(2, headerVals.size());
    assertEquals("val1", headerVals.get(0));
    assertEquals("val2", headerVals.get(1));
    headerVals = attributesGetter.getResponseHeader(null, response, "not-existing");
    assertEquals(0, headerVals.size());
  }
}
