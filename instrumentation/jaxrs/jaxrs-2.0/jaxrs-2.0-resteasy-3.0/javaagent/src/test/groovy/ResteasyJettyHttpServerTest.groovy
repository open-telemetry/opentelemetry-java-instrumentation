/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

class ResteasyJettyHttpServerTest extends JaxRsJettyHttpServerTest {

  // resteasy 3.0.x does not support JAX-RS 2.1
  boolean shouldTestCompletableStageAsync() {
    false
  }
}