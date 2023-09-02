/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.ratpack.client.AbstractRatpackHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.extension.RegisterExtension;

class RatpackHttpClientTest extends AbstractRatpackHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  protected Set<AttributeKey<?>> computeHttpAttributes(URI uri) {
    Set<AttributeKey<?>> attributes = new HashSet<>(super.computeHttpAttributes(uri));
    // underlying netty instrumentation does not provide these
    attributes.remove(SemanticAttributes.NET_PEER_NAME);
    attributes.remove(SemanticAttributes.NET_PEER_PORT);
    return attributes;
  }
}
