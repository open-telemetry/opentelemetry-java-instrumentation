/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

class CxfJettyHttpServerTest extends JaxRsJettyHttpServerTest {

  @Override
  int getResponseCodeOnNonStandardHttpMethod() {
    405
  }
}
