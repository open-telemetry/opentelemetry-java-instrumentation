/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v3_0.hello;

import jakarta.jws.WebService;

@WebService(
    serviceName = "HelloService",
    endpointInterface = "io.opentelemetry.javaagent.instrumentation.jaxws.v3_0.hello.HelloService",
    targetNamespace = "http://opentelemetry.io/test/hello-web-service")
public class HelloServiceImpl implements HelloService {

  @Override
  public String hello(String name) {
    if ("exception".equals(name)) {
      throw new IllegalStateException("hello exception");
    }
    return "Hello " + name;
  }
}
