/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import javax.jws.WebService;

@WebService(
    serviceName = "HelloService",
    endpointInterface = "test.HelloService",
    targetNamespace = "http://opentelemetry.io/test/hello-web-service")
public class HelloServiceImpl implements HelloService {

  @Override
  public String hello(String name) {
    return "Hello " + name;
  }
}
