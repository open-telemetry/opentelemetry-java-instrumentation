/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.assertj.core.util.Strings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ResourceProviderPropertiesCustomizerTest {

  private static final String HOST_ID_RESOURCE_PROVIDER =
      "io.opentelemetry.instrumentation.resources.HostIdResourceProvider";
  private static final String PROVIDER_CLASS_NAME = Provider.class.getName();

  public static class Provider implements ResourceProvider {
    @Override
    public Resource createResource(ConfigProperties config) {
      return Resource.create(Attributes.of(stringKey("key"), "value"));
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

    props.put("otel.traces.exporter", "none");
    props.put("otel.metrics.exporter", "none");
    props.put("otel.logs.exporter", "none");

    Attributes attributes =
        SdkAutoconfigureAccess.getResourceAttributes(
            AutoConfiguredOpenTelemetrySdk.builder().addPropertiesSupplier(() -> props).build());

    if (expectedEnabled) {
      assertThat(attributes.get(stringKey("key"))).isEqualTo("value");
    } else {
      assertThat(attributes.get(stringKey("key"))).isNull();
    }
  }

  @Test
  void hostIdResourceProviderDisabledByDefault() {
    assertThat(disabledProviders(emptyMap())).contains(HOST_ID_RESOURCE_PROVIDER);
  }

  @Test
  void hostIdResourceProviderCanBeEnabled() {
    assertThat(disabledProviders(singletonMap("otel.resource.providers.host-id.enabled", "true")))
        .doesNotContain(HOST_ID_RESOURCE_PROVIDER);
  }

  private static List<String> disabledProviders(Map<String, String> config) {
    Map<String, String> customized =
        new ResourceProviderPropertiesCustomizer()
            .customize(DefaultConfigProperties.createFromMap(config));
    return asList(
        requireNonNull(customized.get(ResourceProviderPropertiesCustomizer.DISABLED_KEY))
            .split(","));
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
