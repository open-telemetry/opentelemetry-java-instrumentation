/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

public class ResourceProviderPropertiesCustomizer implements AutoConfigurationCustomizerProvider {

  private static final Map<String, String> DISABLED_BY_DEFAULT_RESOURCE_PROVIDERS = new HashMap<>();

  static {
    DISABLED_BY_DEFAULT_RESOURCE_PROVIDERS.put(
        "io.opentelemetry.contrib.aws.resource.BeanstalkResourceProvider", "aws");
    DISABLED_BY_DEFAULT_RESOURCE_PROVIDERS.put(
        "io.opentelemetry.contrib.aws.resource.Ec2ResourceProvider", "aws");
    DISABLED_BY_DEFAULT_RESOURCE_PROVIDERS.put(
        "io.opentelemetry.contrib.aws.resource.EcsResourceProvider", "aws");
    DISABLED_BY_DEFAULT_RESOURCE_PROVIDERS.put(
        "io.opentelemetry.contrib.aws.resource.EksResourceProvider", "aws");
    DISABLED_BY_DEFAULT_RESOURCE_PROVIDERS.put(
        "io.opentelemetry.contrib.aws.resource.LambdaResourceProvider", "aws");
    DISABLED_BY_DEFAULT_RESOURCE_PROVIDERS.put(
        "io.opentelemetry.contrib.gcp.resource.GCPResourceProvider", "gcp");
    // for testing
    DISABLED_BY_DEFAULT_RESOURCE_PROVIDERS.put(
        "io.opentelemetry.instrumentation.resources.ResourceProviderPropertiesCustomizerTest$Provider",
        "test");
  }

  static final String DISABLED_KEY = "otel.java.disabled.resource.providers";
  static final String ENABLED_KEY = "otel.java.enabled.resource.providers";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addPropertiesCustomizer(this::customize);
  }

  public Map<String, String> customize(ConfigProperties config) {
    Set<String> enabledProviders = new HashSet<>(config.getList(ENABLED_KEY));

    List<String> enabled = new ArrayList<>();
    List<String> disabled = new ArrayList<>();

    for (Map.Entry<String, String> providerEntry :
        DISABLED_BY_DEFAULT_RESOURCE_PROVIDERS.entrySet()) {
      String providerName = providerEntry.getKey();
      String providerGroup = providerEntry.getValue();
      Boolean explictEnabled =
          config.getBoolean(String.format("otel.resource.providers.%s.enabled", providerGroup));

      if (isEnabled(providerName, enabledProviders, explictEnabled)) {
        enabled.add(providerName);
      } else {
        disabled.add(providerName);
      }
    }

    if (!enabledProviders.isEmpty()) {
      // users has requested specific providers to be enabled only
      enabled.addAll(enabledProviders);
      return Collections.singletonMap(ENABLED_KEY, String.join(",", enabled));
    }

    if (disabled.isEmpty()) {
      // all providers that are disabled by default are enabled, no need to set any properties
      return Collections.emptyMap();
    }

    disabled.addAll(config.getList(DISABLED_KEY));
    return Collections.singletonMap(DISABLED_KEY, String.join(",", disabled));
  }

  private static boolean isEnabled(
      String className, Set<String> enabledProviders, @Nullable Boolean explicitEnabled) {
    if (explicitEnabled != null) {
      return explicitEnabled;
    }
    return !enabledProviders.isEmpty() && enabledProviders.contains(className);
  }

  @Override
  public int order() {
    // make sure it runs AFTER all the user-provided customizers
    return Integer.MAX_VALUE;
  }
}
