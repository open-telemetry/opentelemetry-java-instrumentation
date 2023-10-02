/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import java.util.ArrayList;
import java.util.List;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;

final class PekkoHttpResponseMutator implements HttpServerResponseMutator<HttpResponse> {

  private final List<HttpHeader> headers = new ArrayList<>();

  @Override
  public void appendHeader(HttpResponse response, String name, String value) {
    headers.add(RawHeader.create(name, value));
  }

  List<HttpHeader> getHeaders() {
    return headers;
  }
}
