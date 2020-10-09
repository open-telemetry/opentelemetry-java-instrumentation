/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.khttp;

import java.util.Map;

public class RequestWrapper {
  final String method;
  final String uri;
  final Map<String, String> headers;

  public RequestWrapper(String method, String uri, Map<String, String> headers) {
    this.method = method;
    this.uri = uri;
    this.headers = headers;
  }
}
