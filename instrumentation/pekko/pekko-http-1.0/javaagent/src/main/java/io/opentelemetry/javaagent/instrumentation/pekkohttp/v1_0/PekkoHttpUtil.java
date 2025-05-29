/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0;

import java.util.Collections;
import java.util.List;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;

public class PekkoHttpUtil {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.pekko-http-1.0";

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

  public static String protocolName(HttpRequest request) {
    String protocol = request.protocol().value();
    if (protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  public static String protocolVersion(HttpRequest request) {
    String protocol = request.protocol().value();
    if (protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  private PekkoHttpUtil() {}
}
