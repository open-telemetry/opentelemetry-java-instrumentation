/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import com.ibm.wsspi.genericbnf.HeaderField;
import com.ibm.wsspi.http.channel.HttpRequestMessage;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class LibertyRequest {
  private final HttpRequestMessage httpRequestMessage;
  private final String serverSocketAddress;
  private final int serverSocketPort;
  private final String clientSocketAddress;
  private final int clientSocketPort;

  public LibertyRequest(
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

  public String getMethod() {
    return httpRequestMessage.getMethod();
  }

  public String getScheme() {
    return httpRequestMessage.getScheme();
  }

  public String getRequestUri() {
    return httpRequestMessage.getRequestURI();
  }

  public String getQueryString() {
    return httpRequestMessage.getQueryString();
  }

  public List<String> getAllHeaderNames() {
    return httpRequestMessage.getAllHeaderNames();
  }

  public String getHeaderValue(String name) {
    HeaderField hf = httpRequestMessage.getHeader(name);
    return hf != null ? hf.asString() : null;
  }

  public List<String> getHeaderValues(String name) {
    List<HeaderField> headers = httpRequestMessage.getHeaders(name);
    if (headers.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> stringHeaders = new ArrayList<>(headers.size());
    for (HeaderField header : headers) {
      stringHeaders.add(header.asString());
    }
    return stringHeaders;
  }

  public String getProtocol() {
    return httpRequestMessage.getVersion();
  }

  public String getServerSocketAddress() {
    return serverSocketAddress;
  }

  public int getServerSocketPort() {
    return serverSocketPort;
  }

  public String getClientSocketAddress() {
    return clientSocketAddress;
  }

  public int getClientSocketPort() {
    return clientSocketPort;
  }
}
