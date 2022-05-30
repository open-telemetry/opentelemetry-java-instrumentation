/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import feign.Request;
import feign.RequestTemplate;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

public class OpenFeignTestingOnceTarget implements feign.Target<OpenFeignTestingApi> {

  public final String method;
  public final String url;
  public final Map<String, Collection<String>> headers;

  public OpenFeignTestingOnceTarget(
      String method, String url, Map<String, Collection<String>> headers) {
    this.method = method;
    this.url = url;
    this.headers = headers;
  }

  @Override
  public Class<OpenFeignTestingApi> type() {
    return OpenFeignTestingApi.class;
  }

  @Override
  public String name() {
    return "OpenfeignTestingApi";
  }

  @Override
  public String url() {
    return this.url;
  }

  @Override
  public Request apply(RequestTemplate input) {
    return Request.create(method, url, input.headers(), null, Charset.defaultCharset());
  }
}
