/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.helidon.v4_3;

import io.helidon.webserver.http.ServerResponse;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;

final class HelidonServerResponseMutator implements HttpServerResponseMutator<ServerResponse> {

  static final HelidonServerResponseMutator INSTANCE = new HelidonServerResponseMutator();

  private HelidonServerResponseMutator() {}

  @Override
  public void appendHeader(ServerResponse res, String name, String value) {
    res.header(name, value);
  }
}
