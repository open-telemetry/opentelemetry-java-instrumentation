/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly.client;

import com.ning.http.client.Request;
import io.opentelemetry.context.propagation.TextMapPropagator;

public class GrizzlyInjectAdapter implements TextMapPropagator.Setter<Request> {

  public static final GrizzlyInjectAdapter SETTER = new GrizzlyInjectAdapter();

  @Override
  public void set(Request carrier, String key, String value) {
    carrier.getHeaders().replaceWith(key, value);
  }
}
