/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

public class WildflyArquillianJaxWsTest extends AbstractArquillianJaxWsTest {

  @Override
  protected String getServicePath(String service) {
    if (service.equals("EjbHelloService")) {
      service = "EjbHelloService/EjbHelloServiceImpl";
    }
    return service;
  }
}
