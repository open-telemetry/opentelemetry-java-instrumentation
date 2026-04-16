/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;

final class HttpHeaderSetter implements TextMapSetter<HttpMethod> {

  @Override
  public void set(@Nullable HttpMethod carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.setRequestHeader(new Header(key, value));
  }
}
