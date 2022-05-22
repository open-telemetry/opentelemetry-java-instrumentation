/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.server.ServerBuilder;
import io.opentelemetry.instrumentation.armeria.v1_3.AbstractArmeriaHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArmeriaHttpServerTest extends AbstractArmeriaHttpServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected ServerBuilder configureServer(ServerBuilder sb) {
    return sb;
  }
}
