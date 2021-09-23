/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.context.propagation.TextMapSetter;

public class HttpHeaderSetter implements TextMapSetter<RequestContext> {

  @Override
  public void set(RequestContext carrier, String key, String value) {
    carrier.getRequest().getHeaders().set(key, value);
  }
}
