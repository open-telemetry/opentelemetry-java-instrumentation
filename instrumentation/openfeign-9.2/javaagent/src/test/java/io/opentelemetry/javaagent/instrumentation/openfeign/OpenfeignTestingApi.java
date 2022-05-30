/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import feign.RequestLine;

public interface OpenfeignTestingApi {

  // A fake url is set to avoid initialization errors
  @RequestLine("GET http://localhost:8080")
  String testing();
}
