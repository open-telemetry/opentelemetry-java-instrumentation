/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.MapConverter;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.OtelResourceProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.OtlpExporterProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.PropagationProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.SpringConfigProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.resources.DistroVersionResourceProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.resources.SpringResourceProvider;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.internal.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
  PropagationProperties.class
})
public class OpenTelemetryAutoConfiguration {

  public OpenTelemetryAutoConfiguration() {}

  @Configuration
  @Conditional(SdkEnabled.class)
  @ConditionalOnMissingBean(OpenTelemetry.class)
  public static class OpenTelemetrySdkConfig {

    @Bean
    @ConfigurationPropertiesBinding
    public MapConverter mapConverter() {
      // needed for otlp exporter headers and OtelResourceProperties
      return new MapConverter();
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
    // If you change the bean name, also change it in the OpenTelemetryJdbcDriverAutoConfiguration
    // class
    public OpenTelemetry openTelemetry(
        Environment env,
        OtlpExporterProperties otlpExporterProperties,
        OtelResourceProperties resourceProperties,
        PropagationProperties propagationProperties,
        OpenTelemetrySdkComponentLoader componentLoader) {

      OpenTelemetry openTelemetry =
          AutoConfigureUtil.setComponentLoader(
                  AutoConfigureUtil.setConfigPropertiesCustomizer(
                      AutoConfiguredOpenTelemetrySdk.builder(),
                      c ->
                          SpringConfigProperties.create(
                              env,
                              otlpExporterProperties,
                              resourceProperties,
                              propagationProperties,
                              c)),
                  componentLoader)
              .build()
              .getOpenTelemetrySdk();

      return openTelemetry;
    }
  }

  @Configuration
  @ConditionalOnMissingBean(OpenTelemetry.class)
  @ConditionalOnProperty(name = "otel.sdk.disabled", havingValue = "true")
  public static class DisabledOpenTelemetrySdkConfig {

    @Bean
    public OpenTelemetry openTelemetry() {
      return OpenTelemetry.noop();
    }
  }

  /**
   * The {@link ComponentLoader} is used by the SDK autoconfiguration to load all components, e.g.
   * <a
   * href="https://github.com/open-telemetry/opentelemetry-java/blob/4519a7e90243e5b75b3a46a14c872de88b95a9a1/sdk-extensions/autoconfigure/src/main/java/io/opentelemetry/sdk/autoconfigure/AutoConfiguredOpenTelemetrySdkBuilder.java#L405-L408">here</a>
   */
  public static class OpenTelemetrySdkComponentLoader implements ComponentLoader {
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
