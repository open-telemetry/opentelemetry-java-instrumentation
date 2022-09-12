/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.ws.http.dispatcher.internal.channel;

import com.ibm.wsspi.http.HttpResponse;

// https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.transport.http/src/com/ibm/ws/http/dispatcher/internal/channel/HttpDispatcherLink.java
@SuppressWarnings("OtelInternalJavadoc")
public class HttpDispatcherLink {

  public String getRemoteHostAddress() {
    throw new UnsupportedOperationException();
  }

  public int getRemotePort() {
    throw new UnsupportedOperationException();
  }

  public String getLocalHostAddress() {
    throw new UnsupportedOperationException();
  }

  public int getLocalPort() {
    throw new UnsupportedOperationException();
  }

  public HttpResponse getResponse() {
    throw new UnsupportedOperationException();
  }
}
