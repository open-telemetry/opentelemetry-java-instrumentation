/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.helidon;

import io.helidon.webserver.http.ServerResponse;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;

enum HelidonServerResponseMutator implements HttpServerResponseMutator<ServerResponse> {
  INSTANCE;

  @Override
  public void appendHeader(ServerResponse res, String name, String value) {
    res.header(name, value);
  }
}
