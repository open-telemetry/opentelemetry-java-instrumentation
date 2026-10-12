/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.RedisClient;
import io.lettuce.core.resource.ClientResources;
import io.opentelemetry.instrumentation.lettuce.v5_1.internal.Experimental;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class LettuceSyncClientAuthTest extends AbstractLettuceSyncClientAuthTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected RedisClient createClient(String uri) {
    LettuceTelemetryBuilder builder = LettuceTelemetry.builder(testing().getOpenTelemetry());
    Experimental.setEncodingSpanEventsEnabled(builder, true);
    return RedisClient.create(
        ClientResources.builder().tracing(builder.build().createTracing()).build(), uri);
  }
}
