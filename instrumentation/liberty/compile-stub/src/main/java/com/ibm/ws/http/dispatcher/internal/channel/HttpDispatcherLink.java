/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.ws.http.dispatcher.internal.channel;

// https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.transport.http/src/com/ibm/ws/http/dispatcher/internal/channel/HttpDispatcherLink.java
public class HttpDispatcherLink {

  public int getRemotePort() {
    throw new UnsupportedOperationException();
  }

  public String getRemoteHostAddress() {
    throw new UnsupportedOperationException();
  }

  public String getRemoteHostName(boolean canonical) {
    throw new UnsupportedOperationException();
  }

  public String getRequestedHost() {
    throw new UnsupportedOperationException();
  }

  public int getRequestedPort() {
    throw new UnsupportedOperationException();
  }
}
