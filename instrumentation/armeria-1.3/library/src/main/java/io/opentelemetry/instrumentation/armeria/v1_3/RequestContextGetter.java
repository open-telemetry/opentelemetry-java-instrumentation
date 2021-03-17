/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.server.ServiceRequestContext;
import io.netty.util.AsciiString;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.stream.Collectors;

final class RequestContextGetter implements TextMapGetter<ServiceRequestContext> {

  static final RequestContextGetter INSTANCE = new RequestContextGetter();

  @Override
  public Iterable<String> keys(ServiceRequestContext carrier) {
    return carrier.request().headers().names().stream()
        .map(AsciiString::toString)
        .collect(Collectors.toList());
  }

  @Override
  public String get(ServiceRequestContext carrier, String key) {
    if (carrier == null) {
      return null;
    }
    return carrier.request().headers().get(key);
  }
}
