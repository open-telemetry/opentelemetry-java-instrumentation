/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.hello;

public class BaseHelloService {

  public String hello2(String name) {
    if ("exception".equals(name)) {
      throw new IllegalStateException("hello exception");
    }
    return "Hello " + name;
  }
}
