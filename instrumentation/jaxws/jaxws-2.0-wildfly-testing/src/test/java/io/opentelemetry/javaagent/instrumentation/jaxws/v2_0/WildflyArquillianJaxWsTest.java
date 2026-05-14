/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0;

class WildflyArquillianJaxWsTest extends AbstractArquillianJaxWsTest {

  @Override
  protected String getServicePath(String service) {
    if (service.equals("EjbHelloService")) {
      service = "EjbHelloService/EjbHelloServiceImpl";
    }
    return service;
  }
}
