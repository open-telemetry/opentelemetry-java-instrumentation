/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher.v20_0;

import static java.util.Collections.emptyList;

import com.ibm.wsspi.genericbnf.HeaderField;
import com.ibm.wsspi.http.channel.HttpRequestMessage;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

final class LibertyRequest {
  private final HttpRequestMessage httpRequestMessage;
  @Nullable private final String serverSocketAddress;
  private final int serverSocketPort;
  @Nullable private final String clientSocketAddress;
  private final int clientSocketPort;

  LibertyRequest(
      HttpRequestMessage httpRequestMessage,
      @Nullable InetAddress serverInetAddress,
      int serverSocketPort,
      @Nullable InetAddress clientInetAddress,
      int clientSocketPort) {
    this.httpRequestMessage = httpRequestMessage;
    this.serverSocketAddress =
        serverInetAddress == null ? null : serverInetAddress.getHostAddress();
    this.serverSocketPort = serverSocketPort;
    this.clientSocketAddress =
        clientInetAddress == null ? null : clientInetAddress.getHostAddress();
    this.clientSocketPort = clientSocketPort;
  }

  String getMethod() {
    return httpRequestMessage.getMethod();
  }

  String getScheme() {
    return httpRequestMessage.getScheme();
  }

  String getRequestUri() {
    return httpRequestMessage.getRequestURI();
  }

  String getQueryString() {
    return httpRequestMessage.getQueryString();
  }

  List<String> getAllHeaderNames() {
    return httpRequestMessage.getAllHeaderNames();
  }

  @Nullable
  String getHeaderValue(String name) {
    HeaderField hf = httpRequestMessage.getHeader(name);
    return hf != null ? hf.asString() : null;
  }

  List<String> getHeaderValues(String name) {
    List<HeaderField> headers = httpRequestMessage.getHeaders(name);
    if (headers.isEmpty()) {
      return emptyList();
    }
    List<String> stringHeaders = new ArrayList<>(headers.size());
    for (HeaderField header : headers) {
      stringHeaders.add(header.asString());
    }
    return stringHeaders;
  }

  @Nullable
  String getProtocol() {
    return httpRequestMessage.getVersion();
  }

  @Nullable
  String getServerSocketAddress() {
    return serverSocketAddress;
  }

  int getServerSocketPort() {
    return serverSocketPort;
  }

  @Nullable
  String getClientSocketAddress() {
    return clientSocketAddress;
  }

  int getClientSocketPort() {
    return clientSocketPort;
  }
}
