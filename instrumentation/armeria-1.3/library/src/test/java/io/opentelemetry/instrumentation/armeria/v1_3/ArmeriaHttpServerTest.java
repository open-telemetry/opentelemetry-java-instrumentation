/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.server.ServerBuilder;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import java.util.Collections;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArmeriaHttpServerTest extends AbstractArmeriaHttpServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected ServerBuilder configureServer(ServerBuilder sb) {
    return sb.decorator(
        ArmeriaTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedServerRequestHeaders(
                Collections.singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
            .setCapturedServerResponseHeaders(
                Collections.singletonList(AbstractHttpServerTest.TEST_RESPONSE_HEADER))
            .build()
            .newServiceDecorator());
  }
}
