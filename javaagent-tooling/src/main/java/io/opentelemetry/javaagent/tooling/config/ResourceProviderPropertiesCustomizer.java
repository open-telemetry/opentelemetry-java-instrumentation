/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class ResourceProviderPropertiesCustomizer implements AutoConfigurationCustomizerProvider {

  private static final Set<String> DISABLED_BY_DEFAULT_RESOURCE_PROVIDERS =
      new HashSet<>(
          Arrays.asList(
              "io.opentelemetry.contrib.aws.resource.BeanstalkResourceProvider",
              "io.opentelemetry.contrib.aws.resource.Ec2ResourceProvider",
              "io.opentelemetry.contrib.aws.resource.EcsResourceProvider",
              "io.opentelemetry.contrib.aws.resource.EksResourceProvider",
              "io.opentelemetry.contrib.aws.resource.LambdaResourceProvider",
              "io.opentelemetry.contrib.gcp.resource.GCPResourceProvider",
              // for testing
              "io.opentelemetry.javaagent.tooling.config.ResourceProviderPropertiesCustomizerTest$Provider"));
  static final String DISABLED_KEY = "otel.java.disabled.resource.providers";
  static final String ENABLED_KEY = "otel.java.enabled.resource.providers";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addPropertiesCustomizer(this::customize);
  }

  // VisibleForTesting
  Map<String, String> customize(ConfigProperties config) {
    Set<String> enabledProviders = new HashSet<>(config.getList(ENABLED_KEY));

    List<String> enabled = new ArrayList<>();
    List<String> disabled = new ArrayList<>();

    for (String providerName : DISABLED_BY_DEFAULT_RESOURCE_PROVIDERS) {
      Boolean explictEnabled =
          config.getBoolean(String.format("otel.instrumentation.%s.enabled", providerName));

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
