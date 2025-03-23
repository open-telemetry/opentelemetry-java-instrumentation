/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelMapConverter;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtelResourceProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtelSpringProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtlpExporterProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.SpringConfigProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.resources.DistroVersionResourceProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.resources.SpringResourceProvider;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.internal.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Create {@link io.opentelemetry.api.OpenTelemetry} bean if bean is missing.
 *
 * <p>Adds span exporter beans to the active tracer provider.
 *
 * <p>Updates the sampler probability for the configured {@link TracerProvider}.
 */
@Configuration
@EnableConfigurationProperties({
  OtlpExporterProperties.class,
  OtelResourceProperties.class,
  OtelSpringProperties.class
})
public class OpenTelemetryAutoConfiguration {

  public OpenTelemetryAutoConfiguration() {}

  @Configuration
  @Conditional(SdkEnabled.class)
  @ConditionalOnMissingBean(OpenTelemetry.class)
  static class OpenTelemetrySdkConfig {

    @Bean
    @ConfigurationPropertiesBinding
    public OtelMapConverter otelMapConverter() {
      // needed for otlp exporter headers and OtelResourceProperties
      return new OtelMapConverter();
    }

    @Bean
    public OpenTelemetrySdkComponentLoader openTelemetrySdkComponentLoader(
        ApplicationContext applicationContext) {
      return new OpenTelemetrySdkComponentLoader(applicationContext);
    }

    @Bean
    public ResourceProvider otelSpringResourceProvider(Optional<BuildProperties> buildProperties) {
      return new SpringResourceProvider(buildProperties);
    }

    @Bean
    public ResourceProvider otelDistroVersionResourceProvider() {
      return new DistroVersionResourceProvider();
    }

    @Bean
    public AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk(
        Environment env,
        OtlpExporterProperties otlpExporterProperties,
        OtelResourceProperties resourceProperties,
        OtelSpringProperties otelSpringProperties,
        OpenTelemetrySdkComponentLoader componentLoader) {

      return AutoConfigureUtil.setComponentLoader(
              AutoConfigureUtil.setConfigPropertiesCustomizer(
                  AutoConfiguredOpenTelemetrySdk.builder(),
                  c ->
                      SpringConfigProperties.create(
                          env,
                          otlpExporterProperties,
                          resourceProperties,
                          otelSpringProperties,
                          c)),
              componentLoader)
          .build();
    }

    @Bean
    public OpenTelemetry openTelemetry(
        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
      return autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk();
    }

    /**
     * Expose the {@link ConfigProperties} bean for use in other auto-configurations.
     *
     * <p>Not using spring boot properties directly in order to support {@link
     * io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer#addPropertiesCustomizer(Function)}
     * and {@link
     * io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer#addPropertiesSupplier(Supplier)}.
     */
    @Bean
    public ConfigProperties otelProperties(
        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
      return AutoConfigureUtil.getConfig(autoConfiguredOpenTelemetrySdk);
    }
  }

  @Configuration
  @ConditionalOnMissingBean(OpenTelemetry.class)
  @ConditionalOnProperty(name = "otel.sdk.disabled", havingValue = "true")
  static class DisabledOpenTelemetrySdkConfig {

    @Bean
    @ConfigurationPropertiesBinding
    // Duplicated in OpenTelemetrySdkConfig and DisabledOpenTelemetrySdkConfig to not expose the
    // converter in the public API
    public OtelMapConverter otelMapConverter() {
      // needed for otlp exporter headers and OtelResourceProperties
      // we need this converter, even if the SDK is disabled,
      // because the properties are parsed before the SDK is disabled
      return new OtelMapConverter();
    }

    @Bean
    public OpenTelemetry openTelemetry() {
      return OpenTelemetry.noop();
    }

    @Bean
    public ConfigProperties otelProperties() {
      return DefaultConfigProperties.createFromMap(Collections.emptyMap());
    }
  }

  @Configuration
  @ConditionalOnBean(OpenTelemetry.class)
  @ConditionalOnMissingBean({ConfigProperties.class})
  static class FallbackConfigProperties {
    @Bean
    public ConfigProperties otelProperties() {
      return DefaultConfigProperties.create(Collections.emptyMap());
    }
  }

  /**
   * The {@link ComponentLoader} is used by the SDK autoconfiguration to load all components, e.g.
   * <a
   * href="https://github.com/open-telemetry/opentelemetry-java/blob/4519a7e90243e5b75b3a46a14c872de88b95a9a1/sdk-extensions/autoconfigure/src/main/java/io/opentelemetry/sdk/autoconfigure/AutoConfiguredOpenTelemetrySdkBuilder.java#L405-L408">here</a>
   */
  static class OpenTelemetrySdkComponentLoader implements ComponentLoader {
    private final ApplicationContext applicationContext;

    private final SpiHelper spiHelper =
        SpiHelper.create(OpenTelemetrySdkComponentLoader.class.getClassLoader());

    public OpenTelemetrySdkComponentLoader(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
    }

    @Override
    public <T> Iterable<T> load(Class<T> spiClass) {
      List<T> spi = spiHelper.load(spiClass);
      List<T> beans =
          applicationContext.getBeanProvider(spiClass).orderedStream().collect(Collectors.toList());
      spi.addAll(beans);
      return spi;
    }
  }
}
