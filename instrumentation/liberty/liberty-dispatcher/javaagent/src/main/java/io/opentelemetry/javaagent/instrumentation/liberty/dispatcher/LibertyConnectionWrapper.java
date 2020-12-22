/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import com.ibm.ws.http.dispatcher.internal.channel.HttpDispatcherLink;
import com.ibm.wsspi.http.channel.HttpRequestMessage;

public class LibertyConnectionWrapper {
  private final HttpDispatcherLink httpDispatcherLink;
  private final HttpRequestMessage httpRequestMessage;

  public LibertyConnectionWrapper(
      HttpDispatcherLink httpDispatcherLink, HttpRequestMessage httpRequestMessage) {
    this.httpDispatcherLink = httpDispatcherLink;
    this.httpRequestMessage = httpRequestMessage;
  }

  public int peerPort() {
    return httpDispatcherLink.getRemotePort();
  }

  public String peerHostIP() {
    return httpDispatcherLink.getRemoteHostAddress();
  }

  public String getProtocol() {
    return httpRequestMessage.getVersion();
  }
}
