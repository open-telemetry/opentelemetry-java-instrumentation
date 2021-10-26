/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello

import javax.jws.WebService

@WebService(serviceName = "HelloService", endpointInterface = "hello.HelloService", targetNamespace = "http://opentelemetry.io/test/hello-web-service")
class HelloServiceImpl extends BaseHelloService implements HelloService {

  String hello(String name) {
    if ("exception" == name) {
      throw new Exception("hello exception")
    }
    return "Hello " + name
  }
}
