/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.springframework.http.client.reactive.ClientHttpResponse;

public class HttpProtocolVersion {

  private static final VirtualField<ClientHttpResponse, String> PROTOCOL_VERSION =
      VirtualField.find(ClientHttpResponse.class, String.class);

  public static void set(ClientHttpResponse response, HttpVersion version) {
    String value =
        version.minorVersion() == 0
            ? Integer.toString(version.majorVersion())
            : version.majorVersion() + "." + version.minorVersion();
    PROTOCOL_VERSION.set(response, value);
  }

  private HttpProtocolVersion() {}
}
