/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.ratpack.client.AbstractRatpackPooledHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.semconv.ServerAttributes;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.extension.RegisterExtension;

class RatpackPooledHttpClientTest extends AbstractRatpackPooledHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  protected Set<AttributeKey<?>> computeHttpAttributes(URI uri) {
    Set<AttributeKey<?>> attributes = new HashSet<>(super.computeHttpAttributes(uri));
    // underlying netty instrumentation does not provide these
    attributes.remove(ServerAttributes.SERVER_ADDRESS);
    attributes.remove(ServerAttributes.SERVER_PORT);
    return attributes;
  }
}
