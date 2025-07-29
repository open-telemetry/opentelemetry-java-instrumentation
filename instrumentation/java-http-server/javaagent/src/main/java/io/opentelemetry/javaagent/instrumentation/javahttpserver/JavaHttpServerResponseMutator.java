/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javahttpserver;

import com.sun.net.httpserver.Headers;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;

enum JavaHttpServerResponseMutator implements HttpServerResponseMutator<Headers> {
  INSTANCE;

  @Override
  public void appendHeader(Headers response, String name, String value) {
    response.add(name, value);
  }
}
