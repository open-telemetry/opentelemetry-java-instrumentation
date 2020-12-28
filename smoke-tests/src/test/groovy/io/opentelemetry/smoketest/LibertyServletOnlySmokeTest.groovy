/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer

@AppServer(version = "20.0.0.12", jdk = "8")
class LibertyServletOnlySmokeTest extends LibertySmokeTest {

  protected void customizeContainer(GenericContainer container) {
    container.withClasspathResourceMapping("liberty-servlet.xml", "/config/server.xml", BindMode.READ_ONLY)
  }

}
