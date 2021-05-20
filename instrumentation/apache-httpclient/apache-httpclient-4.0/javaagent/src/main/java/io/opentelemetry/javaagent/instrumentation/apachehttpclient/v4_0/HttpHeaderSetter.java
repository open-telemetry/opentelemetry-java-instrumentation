/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.http.client.methods.HttpUriRequest;

final class HttpHeaderSetter implements TextMapSetter<HttpUriRequest> {

  @Override
  public void set(HttpUriRequest carrier, String key, String value) {
    carrier.setHeader(key, value);
  }
}
