/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

public class TomeeArquillianJaxWsTest extends AbstractArquillianJaxWsTest {

  @Override
  protected String getServicePath(String service) {
    if ("EjbHelloService".equals(service)) {
      service = "webservices/EjbHelloServiceImpl";
    }
    return service;
  }
}
