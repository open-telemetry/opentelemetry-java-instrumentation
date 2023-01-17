/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

public class WildflyArquillianJaxWsTest extends AbstractArquillianJaxWsTest {

  @Override
  protected String getServicePath(String service) {
    if ("EjbHelloService".equals(service)) {
      service = "EjbHelloService/EjbHelloServiceImpl";
    }
    return service;
  }
}
