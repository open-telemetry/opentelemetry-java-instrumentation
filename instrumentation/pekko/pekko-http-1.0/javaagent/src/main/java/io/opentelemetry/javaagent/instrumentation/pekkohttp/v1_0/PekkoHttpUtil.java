/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.internal.HttpProtocolUtil;
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
        .map(httpHeader -> singletonList(httpHeader.value()))
        .orElse(emptyList());
  }

  public static List<String> responseHeader(HttpResponse httpResponse, String name) {
    return httpResponse
        .getHeader(name)
        .map(httpHeader -> singletonList(httpHeader.value()))
        .orElse(emptyList());
  }

  public static String protocolName(HttpRequest request) {
    return HttpProtocolUtil.getProtocol(request.protocol().value());
  }

  public static String protocolVersion(HttpRequest request) {
    // http/2 requests report their protocol as HTTP/2.0, normalize it to 2
    return HttpProtocolUtil.getVersion(request.protocol().value());
  }

  private PekkoHttpUtil() {}
}
