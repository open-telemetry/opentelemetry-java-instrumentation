/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

class WildflyJaxWsTest extends ArquillianJaxWsTest {

  @Override
  def getAddress(String service) {
    if (service == "EjbHelloService") {
      service = "EjbHelloService/EjbHelloServiceImpl"
    }
    return super.getAddress(service)
  }
}
