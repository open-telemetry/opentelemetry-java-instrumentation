/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.List;

public class AkkaHttpUtil {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.akka-http-10.0";

  public static String instrumentationName() {
    return INSTRUMENTATION_NAME;
  }

  public static List<String> requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest
        .getHeader(name)
        .map(httpHeader -> Collections.singletonList(httpHeader.value()))
        .orElse(Collections.emptyList());
  }

  public static List<String> responseHeader(HttpResponse httpResponse, String name) {
    return httpResponse
        .getHeader(name)
        .map(httpHeader -> Collections.singletonList(httpHeader.value()))
        .orElse(Collections.emptyList());
  }

  public static String flavor(HttpRequest httpRequest) {
    switch (httpRequest.protocol().value()) {
      case "HTTP/1.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
      case "HTTP/2.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
      case "HTTP/1.1":
      default:
        return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
    }
  }

  private AkkaHttpUtil() {}
}
