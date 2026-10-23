/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1.classloading;

import org.springframework.http.server.RequestPath;

public class SharedRequestPathFactory {
  public RequestPath getRequestPath() {
    return RequestPath.parse("/hello", "");
  }
}
