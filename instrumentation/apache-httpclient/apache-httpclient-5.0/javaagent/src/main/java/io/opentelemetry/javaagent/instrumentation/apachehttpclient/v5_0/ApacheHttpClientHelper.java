/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.ProtocolVersion;

public class ApacheHttpClientHelper {
  private static final Logger logger = Logger.getLogger(ApacheHttpClientHelper.class.getName());

  public static ApacheHttpClientRequest createRequest(
      Context parentContext, ClassicHttpRequest request) {
    HttpEntity originalEntity = request.getEntity();
    if (originalEntity != null) {
      HttpEntity wrappedHttpEntity = new WrappedHttpEntity(parentContext, originalEntity);
      request.setEntity(wrappedHttpEntity);
    }
    return new ApacheHttpClientRequest(parentContext, request);
  }

  public static List<String> getHeader(MessageHeaders messageHeaders, String name) {
    return headersToList(messageHeaders.getHeaders(name));
  }

  // minimize memory overhead by not using streams
  private static List<String> headersToList(Header[] headers) {
    if (headers.length == 0) {
      return Collections.emptyList();
    }
    List<String> headersList = new ArrayList<>(headers.length);
    for (Header header : headers) {
      headersList.add(header.getValue());
    }
    return headersList;
  }

  public static String getFlavor(ProtocolVersion protocolVersion) {
    if (protocolVersion == null) {
      return null;
    }
    String protocol = protocolVersion.getProtocol();
    if (!protocol.equals("HTTP")) {
      return null;
    }
    int major = protocolVersion.getMajor();
    int minor = protocolVersion.getMinor();
    if (major == 1 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
    }
    if (major == 1 && minor == 1) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
    }
    if (major == 2 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    }
    logger.log(Level.FINE, "unexpected http protocol version: {0}", protocolVersion);
    return null;
  }

  private ApacheHttpClientHelper() {}
}
