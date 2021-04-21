/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

class JerseyJettyHttpServerTest extends JaxRsJettyHttpServerTest {

  @Override
  boolean asyncCancelHasSendError() {
    true
  }
}