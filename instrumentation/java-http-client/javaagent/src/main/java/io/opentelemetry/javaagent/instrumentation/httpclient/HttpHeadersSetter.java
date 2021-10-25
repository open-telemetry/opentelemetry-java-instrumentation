/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO should this class implement TextMapSetter at all?
/** Context propagation is initiated via {@link HttpHeadersInstrumentation}. */
public class HttpHeadersSetter implements TextMapSetter<HttpRequest> {

  private final ContextPropagators contextPropagators;

  public HttpHeadersSetter(ContextPropagators contextPropagators) {
    this.contextPropagators = contextPropagators;
  }

  @Override
  public void set(HttpRequest carrier, String key, String value) {
    // Don't do anything because headers are immutable
  }

  public HttpHeaders inject(HttpHeaders original) {
    Map<String, List<String>> headerMap = new HashMap<>(original.map());

    contextPropagators
        .getTextMapPropagator()
        .inject(
            Context.current(),
            headerMap,
            (carrier, key, value) -> carrier.put(key, Collections.singletonList(value)));

    return HttpHeaders.of(headerMap, (s, s2) -> true);
  }
}
