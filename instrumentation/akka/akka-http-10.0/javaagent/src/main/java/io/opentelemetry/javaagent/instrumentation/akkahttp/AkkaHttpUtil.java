/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
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

  public static String httpVersion(HttpRequest httpRequest) {
    String protocol = httpRequest.protocol().value();
    if (protocol.startsWith("HTTP/")) {
      protocol = protocol.substring("HTTP/".length());
    }
    return protocol;
  }

  private AkkaHttpUtil() {}
}
