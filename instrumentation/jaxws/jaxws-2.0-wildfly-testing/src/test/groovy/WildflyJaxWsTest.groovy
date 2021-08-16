/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

class WildflyJaxWsTest extends ArquillianJaxWsTest {

  @Override
  def getServicePath(String service) {
    if (service == "EjbHelloService") {
      service = "EjbHelloService/EjbHelloServiceImpl"
    }
    return service
  }
}
