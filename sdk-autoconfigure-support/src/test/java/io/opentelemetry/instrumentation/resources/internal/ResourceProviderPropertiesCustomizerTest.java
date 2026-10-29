/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_ARCH;
import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_NAME;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mockStatic;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.resources.HostResource;
import io.opentelemetry.instrumentation.resources.HostResourceProvider;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.assertj.core.util.Strings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

class ResourceProviderPropertiesCustomizerTest {

  private static final String PROVIDER_CLASS_NAME = Provider.class.getName();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  public static class Provider implements ResourceProvider {
    @Override
    public Resource createResource(ConfigProperties config) {
      return Resource.create(
          Attributes.of(
              stringKey("key"), "value", HOST_NAME, "provider-host", HOST_ARCH, "provider-arch"));
    }
  }

  @SuppressWarnings("BooleanParameter")
  @ParameterizedTest
  @MethodSource("enabledTestCases")
  void enabled(
      boolean expectedEnabled,
      Set<String> enabledProviders,
      Set<String> disabledProviders,
      @Nullable Boolean explicitEnabled) {
    Map<String, String> props = new HashMap<>();
    props.put(
        ResourceProviderPropertiesCustomizer.ENABLED_KEY, Strings.join(enabledProviders).with(","));
    props.put(
        ResourceProviderPropertiesCustomizer.DISABLED_KEY,
        Strings.join(disabledProviders).with(","));

    if (explicitEnabled != null) {
      props.put("otel.resource.providers.test.enabled", Boolean.toString(explicitEnabled));
    }

    Attributes attributes = configure(props);

    if (expectedEnabled) {
      assertThat(attributes.get(stringKey("key"))).isEqualTo("value");
    } else {
      assertThat(attributes.get(stringKey("key"))).isNull();
    }
  }

  @Test
  void preservesEarlierHostDescriptions() {
    Map<String, String> props = new HashMap<>();
    props.put(
        ResourceProviderPropertiesCustomizer.ENABLED_KEY,
        PROVIDER_CLASS_NAME + "," + HostResourceProvider.class.getName());
    Resource descriptions =
        Resource.create(Attributes.of(HOST_NAME, "local-host", HOST_ARCH, "local-arch"));
    try (MockedStatic<HostResource> host = mockStatic(HostResource.class)) {
      host.when(() -> HostResource.create(true)).thenReturn(descriptions);

      Attributes attributes = configure(props);

      assertThat(attributes.get(HOST_NAME)).isEqualTo("provider-host");
      assertThat(attributes.get(HOST_ARCH)).isEqualTo("provider-arch");
      host.verify(() -> HostResource.create(true));
    }
  }

  private Attributes configure(Map<String, String> props) {
    props.put("otel.traces.exporter", "none");
    props.put("otel.metrics.exporter", "none");
    props.put("otel.logs.exporter", "none");
    AutoConfiguredOpenTelemetrySdk sdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesCustomizer(config -> props)
            .disableShutdownHook()
            .build();
    cleanup.deferCleanup(sdk.getOpenTelemetrySdk());
    return SdkAutoconfigureAccess.getResourceAttributes(sdk);
  }

  private static Stream<Arguments> enabledTestCases() {
    return Stream.of(
        argumentSet("explicitEnabled", true, emptySet(), emptySet(), true),
        argumentSet("explicitEnabledFalse", false, emptySet(), emptySet(), false),
        argumentSet("enabledProvidersEmpty", false, emptySet(), emptySet(), null),
        argumentSet(
            "enabledProvidersContains", true, singleton(PROVIDER_CLASS_NAME), emptySet(), null),
        argumentSet(
            "enabledProvidersNotContains", false, singleton("otherClassName"), emptySet(), null),
        argumentSet(
            "disabledProvidersContains", false, emptySet(), singleton(PROVIDER_CLASS_NAME), null),
        argumentSet(
            "disabledProvidersNotContains", false, emptySet(), singleton("otherClassName"), null),
        argumentSet("defaultEnabledFalse", false, emptySet(), emptySet(), null));
  }
}
