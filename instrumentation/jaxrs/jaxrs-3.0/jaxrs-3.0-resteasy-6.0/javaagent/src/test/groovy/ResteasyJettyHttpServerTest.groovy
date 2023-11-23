/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

class ResteasyJettyHttpServerTest extends JaxRsJettyHttpServerTest {

  @Override
  int getResponseCodeOnNonStandardHttpMethod() {
    500
  }
}
