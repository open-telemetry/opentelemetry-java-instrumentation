/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.playws;

import io.opentelemetry.context.propagation.TextMapPropagator;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;

public class HeadersInjectAdapter implements TextMapPropagator.Setter<HttpHeaders> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void set(HttpHeaders carrier, String key, String value) {
    carrier.add(key, value);
  }
}
