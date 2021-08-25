/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.context.propagation.TextMapSetter;
import play.shaded.ahc.org.asynchttpclient.Request;

public class HttpHeaderSetter implements TextMapSetter<Request> {

  @Override
  public void set(Request carrier, String key, String value) {
    carrier.getHeaders().set(key, value);
  }
}
