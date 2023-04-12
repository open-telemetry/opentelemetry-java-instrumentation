/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.MimeHeaders;

public enum GrizzlyHttpResponseMutator implements HttpServerResponseMutator<HttpResponsePacket> {
  INSTANCE;

  @Override
  public void appendHeader(HttpResponsePacket response, String name, String value) {
    MimeHeaders headers = response.getHeaders();
    DataChunk data = headers.getValue(name);
    if (data == null) {
      data = headers.addValue(name);
    }
    if (data.getLength() > 0) {
      data.setString(data.toString() + "," + value);
    } else {
      data.setString(value);
    }
  }
}
