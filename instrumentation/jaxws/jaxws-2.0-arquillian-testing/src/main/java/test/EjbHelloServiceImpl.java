/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import javax.ejb.Stateless;
import javax.jws.WebService;

@WebService(
    serviceName = "EjbHelloService",
    endpointInterface = "test.HelloService",
    targetNamespace = "http://opentelemetry.io/test/hello-web-service")
@Stateless
public class EjbHelloServiceImpl implements HelloService {

  @Override
  public String hello(String name) {
    return "Hello " + name;
  }
}
