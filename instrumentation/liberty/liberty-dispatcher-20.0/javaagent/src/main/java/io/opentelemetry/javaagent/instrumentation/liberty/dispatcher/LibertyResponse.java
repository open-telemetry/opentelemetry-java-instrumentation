/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import com.ibm.ws.http.dispatcher.internal.channel.HttpDispatcherLink;
import com.ibm.wsspi.http.HttpResponse;
import com.ibm.wsspi.http.channel.values.StatusCodes;
import java.util.Collections;
import java.util.List;

public class LibertyResponse {
  private final HttpDispatcherLink httpDispatcherLink;
  private final StatusCodes code;

  public LibertyResponse(HttpDispatcherLink httpDispatcherLink, StatusCodes code) {
    this.httpDispatcherLink = httpDispatcherLink;
    this.code = code;
  }

  public int getStatus() {
    return code.getIntCode();
  }

  public List<String> getHeaderValues(String name) {
    HttpResponse response = httpDispatcherLink.getResponse();
    // response is set to null on destroy(), so it shouldn't really ever be null in the middle of
    // request processing, but just to be safe let's check it
    return response == null ? Collections.emptyList() : response.getHeaders(name);
  }
}
