/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher.v20_0;

import static java.util.Collections.emptyList;

import com.ibm.ws.http.dispatcher.internal.channel.HttpDispatcherLink;
import com.ibm.wsspi.http.HttpResponse;
import com.ibm.wsspi.http.channel.values.StatusCodes;
import java.util.List;

final class LibertyResponse {
  private final HttpDispatcherLink httpDispatcherLink;
  private final StatusCodes code;

  LibertyResponse(HttpDispatcherLink httpDispatcherLink, StatusCodes code) {
    this.httpDispatcherLink = httpDispatcherLink;
    this.code = code;
  }

  int getStatus() {
    return code.getIntCode();
  }

  List<String> getHeaderValues(String name) {
    HttpResponse response = httpDispatcherLink.getResponse();
    // response is set to null on destroy(), so it shouldn't really ever be null in the middle of
    // request processing, but just to be safe let's check it
    return response == null ? emptyList() : response.getHeaders(name);
  }
}
