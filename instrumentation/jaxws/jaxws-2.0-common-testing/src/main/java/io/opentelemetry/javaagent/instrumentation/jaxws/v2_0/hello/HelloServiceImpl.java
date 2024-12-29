/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.hello;

import javax.jws.WebService;

@WebService(
    serviceName = "HelloService",
    endpointInterface = "io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.hello.HelloService",
    targetNamespace = "http://opentelemetry.io/test/hello-web-service")
public class HelloServiceImpl extends BaseHelloService implements HelloService {

  @Override
  public String hello(String name) {
    if ("exception".equals(name)) {
      throw new IllegalStateException("hello exception");
    }
    return "Hello " + name;
  }
}
