/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class ServerAttributesExtractorTest {

  static class TestServerAttributesGetter
      implements ServerAttributesGetter<Map<String, String>, Void> {

    @Nullable
    @Override
    public String getServerAddress(Map<String, String> request) {
      return request.get("address");
    }

    @Nullable
    @Override
    public Integer getServerPort(Map<String, String> request) {
      String port = request.get("port");
      return port == null ? null : Integer.parseInt(port);
    }

    @Nullable
    @Override
    public String getServerSocketDomain(Map<String, String> request, @Nullable Void response) {
      return request.get("socketDomain");
    }

    @Nullable
    @Override
    public String getServerSocketAddress(Map<String, String> request, @Nullable Void response) {
      return request.get("socketAddress");
    }

    @Nullable
    @Override
    public Integer getServerSocketPort(Map<String, String> request, @Nullable Void response) {
      String port = request.get("socketPort");
      return port == null ? null : Integer.parseInt(port);
    }
  }

  @Test
  void allAttributes() {
    Map<String, String> request = new HashMap<>();
    request.put("address", "opentelemetry.io");
    request.put("port", "80");
    request.put("socketDomain", "proxy.opentelemetry.io");
    request.put("socketAddress", "1.2.3.4");
    request.put("socketPort", "8080");

    AttributesExtractor<Map<String, String>, Void> extractor =
        ServerAttributesExtractor.create(new TestServerAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.SERVER_ADDRESS, "opentelemetry.io"),
            entry(SemanticAttributes.SERVER_PORT, 80L));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, null, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.SERVER_SOCKET_DOMAIN, "proxy.opentelemetry.io"),
            entry(SemanticAttributes.SERVER_SOCKET_ADDRESS, "1.2.3.4"),
            entry(SemanticAttributes.SERVER_SOCKET_PORT, 8080L));
  }

  @Test
  void noAttributes() {
    AttributesExtractor<Map<String, String>, Void> extractor =
        ServerAttributesExtractor.create(new TestServerAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), emptyMap());
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), emptyMap(), null, null);
    assertThat(endAttributes.build()).isEmpty();
  }

  @Test
  void doesNotSetNegativePortValues() {
    Map<String, String> request = new HashMap<>();
    request.put("address", "opentelemetry.io");
    request.put("port", "-12");
    request.put("socketAddress", "1.2.3.4");
    request.put("socketPort", "-42");

    AttributesExtractor<Map<String, String>, Void> extractor =
        ServerAttributesExtractor.create(new TestServerAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build())
        .containsOnly(entry(SemanticAttributes.SERVER_ADDRESS, "opentelemetry.io"));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, null, null);
    assertThat(endAttributes.build())
        .containsOnly(entry(SemanticAttributes.SERVER_SOCKET_ADDRESS, "1.2.3.4"));
  }

  @Test
  void doesNotSetDuplicates() {
    Map<String, String> request = new HashMap<>();
    request.put("address", "1.2.3.4");
    request.put("port", "80");
    request.put("socketDomain", "1.2.3.4");
    request.put("socketAddress", "1.2.3.4");
    request.put("socketPort", "80");

    AttributesExtractor<Map<String, String>, Void> extractor =
        ServerAttributesExtractor.create(new TestServerAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.SERVER_ADDRESS, "1.2.3.4"),
            entry(SemanticAttributes.SERVER_PORT, 80L));

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, null, null);
    assertThat(endAttributes.build()).isEmpty();
  }
}
