/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import java.net.http.HttpHeaders;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class HttpHeadersSetter {

  private final ContextPropagators contextPropagators;

  public HttpHeadersSetter(ContextPropagators contextPropagators) {
    this.contextPropagators = contextPropagators;
  }

  public HttpHeaders inject(HttpHeaders original, Context context) {
    Map<String, List<String>> headerMap = new HashMap<>(original.map());

    contextPropagators
        .getTextMapPropagator()
        .inject(
            context,
            headerMap,
            (carrier, key, value) -> {
              if (carrier != null) {
                carrier.put(key, Collections.singletonList(value));
              }
            });

    return HttpHeaders.of(headerMap, (s, s2) -> true);
  }
}
