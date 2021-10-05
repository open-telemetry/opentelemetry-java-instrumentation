/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import com.ibm.ws.http.dispatcher.internal.channel.HttpDispatcherLink;
import com.ibm.wsspi.genericbnf.HeaderField;
import com.ibm.wsspi.http.channel.HttpRequestMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LibertyRequest {
  private final HttpDispatcherLink httpDispatcherLink;
  private final HttpRequestMessage httpRequestMessage;
  private boolean completed;

  public LibertyRequest(
      HttpDispatcherLink httpDispatcherLink, HttpRequestMessage httpRequestMessage) {
    this.httpDispatcherLink = httpDispatcherLink;
    this.httpRequestMessage = httpRequestMessage;
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

  public int getServerPort() {
    return httpDispatcherLink.getRequestedPort();
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

  public int peerPort() {
    return httpDispatcherLink.getRemotePort();
  }

  public String peerIp() {
    return httpDispatcherLink.getRemoteHostAddress();
  }

  public String peerName() {
    return httpDispatcherLink.getRemoteHostName(false);
  }

  public String getProtocol() {
    return httpRequestMessage.getVersion();
  }

  public boolean isCompleted() {
    return completed;
  }

  public void setCompleted() {
    completed = true;
  }
}
