/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello;

import jakarta.jws.WebService;

@WebService(serviceName = "HelloService", endpointInterface = "hello.HelloService", targetNamespace = "http://opentelemetry.io/test/hello-web-service")
class HelloServiceImpl extends BaseHelloService implements HelloService {

  @Override
  public String hello(String name) throws Exception {
    if ("exception".equals(name)) {
      throw new Exception("hello exception");
    }
    return "Hello " + name;
  }
}
