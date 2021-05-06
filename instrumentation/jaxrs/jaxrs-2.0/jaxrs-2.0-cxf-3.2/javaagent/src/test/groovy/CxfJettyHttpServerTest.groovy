/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

class CxfJettyHttpServerTest extends JaxRsJettyHttpServerTest {

  @Override
  boolean hasFrameworkInstrumentation() {
    false
  }
}