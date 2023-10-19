/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import static io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatHelper.messageBytesToString;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.MessageBytes;

public class TomcatHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {

  @Override
  public String getHttpRequestMethod(Request request) {
    return messageBytesToString(request.method());
  }

  @Override
  @Nullable
  public String getUrlScheme(Request request) {
    MessageBytes schemeMessageBytes = request.scheme();
    return schemeMessageBytes.isNull() ? "http" : messageBytesToString(schemeMessageBytes);
  }

  @Nullable
  @Override
  public String getUrlPath(Request request) {
    return messageBytesToString(request.requestURI());
  }

  @Nullable
  @Override
  public String getUrlQuery(Request request) {
    return messageBytesToString(request.queryString());
  }

  @Override
  public List<String> getHttpRequestHeader(Request request, String name) {
    return Collections.list(request.getMimeHeaders().values(name));
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      Request request, Response response, @Nullable Throwable error) {
    return response.getStatus();
  }

  @Override
  public List<String> getHttpResponseHeader(Request request, Response response, String name) {
    return Collections.list(response.getMimeHeaders().values(name));
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(Request request, @Nullable Response response) {
    String protocol = messageBytesToString(request.protocol());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Request request, @Nullable Response response) {
    String protocol = messageBytesToString(request.protocol());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(Request request) {
    return messageBytesToString(request.serverName());
  }

  @Override
  public Integer getServerPort(Request request) {
    return request.getServerPort();
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(Request request, @Nullable Response response) {
    request.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, request);
    return messageBytesToString(request.remoteAddr());
  }

  @Override
  @Nullable
  public Integer getNetworkPeerPort(Request request, @Nullable Response response) {
    request.action(ActionCode.REQ_REMOTEPORT_ATTRIBUTE, request);
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String getNetworkLocalAddress(Request request, @Nullable Response response) {
    request.action(ActionCode.REQ_LOCAL_ADDR_ATTRIBUTE, request);
    return messageBytesToString(request.localAddr());
  }

  @Nullable
  @Override
  public Integer getNetworkLocalPort(Request request, @Nullable Response response) {
    request.action(ActionCode.REQ_LOCALPORT_ATTRIBUTE, request);
    return request.getLocalPort();
  }
}
