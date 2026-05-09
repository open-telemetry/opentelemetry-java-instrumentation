/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.common.Attributes;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ResourceProviderPropertiesCustomizerTest {

  private static final String PROVIDER_CLASS_NAME = Provider.class.getName();

  public static class Provider implements ResourceProvider {
    @Override
    public Resource createResource(ConfigProperties config) {
      return Resource.create(Attributes.of(stringKey("key"), "value"));
    }
  }

  @SuppressWarnings("BooleanParameter")
  @ParameterizedTest(name = "{0}")
  @MethodSource("enabledTestCases")
  void enabled(
      String name,
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

  private static Stream<Arguments> enabledTestCases() {
    return Stream.of(
        arguments("explicitEnabled", true, emptySet(), emptySet(), true),
        arguments("explicitEnabledFalse", false, emptySet(), emptySet(), false),
        arguments("enabledProvidersEmpty", false, emptySet(), emptySet(), null),
        arguments(
            "enabledProvidersContains", true, singleton(PROVIDER_CLASS_NAME), emptySet(), null),
        arguments(
            "enabledProvidersNotContains", false, singleton("otherClassName"), emptySet(), null),
        arguments(
            "disabledProvidersContains", false, emptySet(), singleton(PROVIDER_CLASS_NAME), null),
        arguments(
            "disabledProvidersNotContains", false, emptySet(), singleton("otherClassName"), null),
        arguments("defaultEnabledFalse", false, emptySet(), emptySet(), null));
  }
}
