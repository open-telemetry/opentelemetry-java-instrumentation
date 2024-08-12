/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello;

import javax.jws.WebService;

@WebService(
    serviceName = "HelloService",
    endpointInterface = "hello.HelloService",
    targetNamespace = "http://opentelemetry.io/test/hello-web-service")
public class HelloServiceImpl extends BaseHelloService implements HelloService {

  @Override
  public String hello(String name) throws Exception {
    if ("exception".equals(name)) {
      throw new Exception("hello exception");
    }
    return "Hello " + name;
  }
}
