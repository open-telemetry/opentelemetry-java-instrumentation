/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.internal.ServiceMappingResolver;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerServiceAttributesExtractorTest {

  @Mock ServerAttributesGetter<String> attributesGetter;

  @Test
  void shouldNotSetAnyValueIfNetExtractorReturnsNulls() {
    // given
    ServiceMappingResolver resolver = createResolver(mapping("1.2.3.4", "myService", null));

    AttributesExtractor<String, String> underTest =
        new ServerServiceAttributesExtractor<>(attributesGetter, resolver);

    Context context = Context.root();

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, context, "request");
    underTest.onEnd(attributes, context, "request", "response", null);

    // then
    assertThat(attributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldNotSetAnyValueIfTargetDoesNotMatch() {
    // given
    ServiceMappingResolver resolver = createResolver(mapping("example.com", "myService", null));

    AttributesExtractor<String, String> underTest =
        new ServerServiceAttributesExtractor<>(attributesGetter, resolver);

    when(attributesGetter.getServerAddress(any())).thenReturn("example2.com");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, "request", "response", null);

    // then
    assertThat(startAttributes.build().isEmpty()).isTrue();
    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldSetServerServiceIdentityIfItMatches() {
    // given
    ServiceMappingResolver resolver =
        createResolver(
            mapping("example.com", "myService", null),
            mapping("1.2.3.4", "someOtherService", null));

    AttributesExtractor<String, String> underTest =
        new ServerServiceAttributesExtractor<>(attributesGetter, resolver);

    when(attributesGetter.getServerAddress(any())).thenReturn("example.com");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, "request", "response", null);

    // then
    assertThat(startAttributes.build()).isEmpty();
    Attributes attrs = endAttributes.build();
    assertThat(attrs).containsOnly(entry(stringKey("server.service.name"), "myService"));
  }

  @Test
  void shouldMatchConfiguredTargetExactly() {
    String target = "db1.example:5432,db2.example:5432";
    ServiceMappingResolver resolver = createResolver(mapping(target, "myService", null));
    AttributesExtractor<String, String> underTest =
        new ServerServiceAttributesExtractor<>(attributesGetter, resolver);
    when(attributesGetter.getServerAddress(any())).thenReturn(target);

    AttributesBuilder attributes = Attributes.builder();
    underTest.onEnd(attributes, Context.root(), "request", "response", null);

    Attributes attrs = attributes.build();
    assertThat(attrs).containsOnly(entry(stringKey("server.service.name"), "myService"));
  }

  @SuppressWarnings("deprecation")
  @Test
  void deprecatedExtractorForwardsToServerServiceExtractor() {
    AttributesExtractor<String, String> underTest =
        ServicePeerAttributesExtractor.create(
            attributesGetter, createOpenTelemetry(mapping("example.com", "myService", null)));
    when(attributesGetter.getServerAddress(any())).thenReturn("example.com");

    AttributesBuilder attributes = Attributes.builder();
    underTest.onEnd(attributes, Context.root(), "request", "response", null);

    assertThat(attributes.build())
        .containsOnly(entry(stringKey("server.service.name"), "myService"));
  }

  private static ServiceMappingResolver createResolver(DeclarativeConfigProperties... entries) {
    return new ServiceMappingResolver(createOpenTelemetry(entries));
  }

  private static ExtendedOpenTelemetry createOpenTelemetry(DeclarativeConfigProperties... entries) {
    ExtendedOpenTelemetry otel = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig = mock(DeclarativeConfigProperties.class);
    when(otel.getInstrumentationConfig("common")).thenReturn(commonConfig);
    List<DeclarativeConfigProperties> entryList = asList(entries);
    when(commonConfig.getStructuredList("server_service_mapping", emptyList()))
        .thenReturn(entryList);
    return otel;
  }

  private static DeclarativeConfigProperties mapping(
      String target, @Nullable String serviceName, @Nullable String serviceNamespace) {
    DeclarativeConfigProperties props = mock(DeclarativeConfigProperties.class);
    when(props.getString("target")).thenReturn(target);
    when(props.getString("service_name")).thenReturn(serviceName);
    when(props.getString("service_namespace")).thenReturn(serviceNamespace);
    return props;
  }
}
