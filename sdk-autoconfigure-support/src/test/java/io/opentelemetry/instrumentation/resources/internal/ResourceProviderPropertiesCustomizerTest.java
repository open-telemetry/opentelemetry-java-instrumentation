/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
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
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ResourceProviderPropertiesCustomizerTest {

  public static final class Provider implements ResourceProvider {
    @Override
    public Resource createResource(ConfigProperties config) {
      return Resource.create(Attributes.of(AttributeKey.stringKey("key"), "value"));
    }
  }

  private static class EnabledTestCase {
    private final String name;
    private final boolean result;
    private final Set<String> enabledProviders;
    private final Set<String> disabledProviders;
    private final Boolean explicitEnabled;

    private EnabledTestCase(
        String name,
        boolean result,
        Set<String> enabledProviders,
        Set<String> disabledProviders,
        @Nullable Boolean explicitEnabled) {
      this.name = name;
      this.result = result;
      this.enabledProviders = enabledProviders;
      this.disabledProviders = disabledProviders;
      this.explicitEnabled = explicitEnabled;
    }
  }

  @SuppressWarnings("BooleanParameter")
  @TestFactory
  Stream<DynamicTest> enabledTestCases() {
    String className =
        "io.opentelemetry.instrumentation.resources.internal.ResourceProviderPropertiesCustomizerTest$Provider";
    return Stream.of(
            new EnabledTestCase("explicitEnabled", true, emptySet(), emptySet(), true),
            new EnabledTestCase("explicitEnabledFalse", false, emptySet(), emptySet(), false),
            new EnabledTestCase("enabledProvidersEmpty", false, emptySet(), emptySet(), null),
            new EnabledTestCase(
                "enabledProvidersContains", true, singleton(className), emptySet(), null),
            new EnabledTestCase(
                "enabledProvidersNotContains",
                false,
                singleton("otherClassName"),
                emptySet(),
                null),
            new EnabledTestCase(
                "disabledProvidersContains", false, emptySet(), singleton(className), null),
            new EnabledTestCase(
                "disabledProvidersNotContains",
                false,
                emptySet(),
                singleton("otherClassName"),
                null),
            new EnabledTestCase("defaultEnabledFalse", false, emptySet(), emptySet(), null))
        .map(
            tc ->
                DynamicTest.dynamicTest(
                    tc.name,
                    () -> {
                      Map<String, String> props = new HashMap<>();
                      props.put(
                          ResourceProviderPropertiesCustomizer.ENABLED_KEY,
                          Strings.join(tc.enabledProviders).with(","));
                      props.put(
                          ResourceProviderPropertiesCustomizer.DISABLED_KEY,
                          Strings.join(tc.disabledProviders).with(","));

                      if (tc.explicitEnabled != null) {
                        props.put(
                            "otel.resource.providers.test.enabled",
                            Boolean.toString(tc.explicitEnabled));
                      }

                      props.put("otel.traces.exporter", "none");
                      props.put("otel.metrics.exporter", "none");
                      props.put("otel.logs.exporter", "none");

                      Attributes attributes =
                          SdkAutoconfigureAccess.getResourceAttributes(
                              AutoConfiguredOpenTelemetrySdk.builder()
                                  .addPropertiesSupplier(() -> props)
                                  .build());

                      if (tc.result) {
                        assertThat(attributes.get(AttributeKey.stringKey("key")))
                            .isEqualTo("value");
                      } else {
                        assertThat(attributes.get(AttributeKey.stringKey("key"))).isNull();
                      }
                    }));
  }
}
