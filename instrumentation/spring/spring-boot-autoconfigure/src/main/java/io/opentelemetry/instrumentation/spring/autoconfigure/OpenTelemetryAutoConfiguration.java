/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.GlobalConfigProvider;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.sdk.DeclarativeConfigPropertiesBridge;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelMapConverter;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtelResourceProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtelSpringProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtlpExporterProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.SpringConfigProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.resources.DistroVersionResourceProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.resources.SpringResourceProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.internal.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * Create {@link io.opentelemetry.api.OpenTelemetry} bean if bean is missing.
 *
 * <p>Adds span exporter beans to the active tracer provider.
 *
 * <p>Updates the sampler probability for the configured {@link TracerProvider}.
 */
@Configuration
public class OpenTelemetryAutoConfiguration {
  private static final Logger logger =
      LoggerFactory.getLogger(OpenTelemetryAutoConfiguration.class);

  public OpenTelemetryAutoConfiguration() {}

  @Configuration
  @Conditional(SdkEnabled.class)
  @ConditionalOnMissingBean(OpenTelemetry.class)
  static class OpenTelemetrySdkConfig {
    @Bean
    public ResourceProvider otelSpringResourceProvider(Optional<BuildProperties> buildProperties) {
      return new SpringResourceProvider(buildProperties);
    }

    @Bean
    public ResourceProvider otelDistroVersionResourceProvider() {
      return new DistroVersionResourceProvider();
    }

    @Configuration
    @EnableConfigurationProperties({
      OtlpExporterProperties.class,
      OtelResourceProperties.class,
      OtelSpringProperties.class
    })
    @ConditionalOnProperty(name = "otel.file_format", matchIfMissing = true, havingValue = "never")
    static class PropertiesConfig {

      @Bean
      @ConfigurationPropertiesBinding
      OtelMapConverter otelMapConverter() {
        return new OtelMapConverter();
      }

      @Bean
      public AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk(
          Environment env,
          OtlpExporterProperties otlpExporterProperties,
          OtelResourceProperties resourceProperties,
          OtelSpringProperties otelSpringProperties,
          ApplicationContext applicationContext) {

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
                new OpenTelemetrySdkComponentLoader(applicationContext))
            .build();
      }

      @Bean
      public OpenTelemetry openTelemetry(
          AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
        logStart();
        return autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk();
      }

      @Bean
      public InstrumentationConfig instrumentationConfig(ConfigProperties properties) {
        return new ConfigPropertiesBridge(properties);
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
  @ConditionalOnProperty(name = "otel.file_format")
  static class EmbeddedConfigFileConfig {

    @Bean
    public OpenTelemetryConfigurationModel openTelemetryConfigurationModel(
        ConfigurableEnvironment environment) throws IOException {
      return EmbeddedConfigFile.extractModel(environment);
    }

    @Bean
    public OpenTelemetry openTelemetry(
        OpenTelemetryConfigurationModel model, ApplicationContext applicationContext) {
      OpenTelemetrySdk sdk = DeclarativeConfiguration.create(model, new OpenTelemetrySdkComponentLoader(applicationContext));

      Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));

      SdkConfigProvider configProvider = SdkConfigProvider.create(model);
      GlobalConfigProvider.set(configProvider);

      System.out.println(
          "OpenTelemetry SDK initialized with configuration from: " + sdk); // todo remove

      // todo declarative configuration
      // todo map converter not needed here, because the declarative configuration is not using
      // environment variables

      logStart();

      return null;
    }

    @Bean
    public InstrumentationConfig instrumentationConfig(
        ConfigProperties properties, OpenTelemetryConfigurationModel model) {
      return new ConfigPropertiesBridge(properties, SdkConfigProvider.create(model));
    }

    /**
     * Expose the {@link ConfigProperties} bean for use in other auto-configurations.
     *
     * <p>Not using spring boot properties directly, because declarative configuration does not
     * integrate with spring boot properties.
     */
    @Bean
    public ConfigProperties otelProperties(OpenTelemetryConfigurationModel model) {
      return DeclarativeConfigPropertiesBridge.create(
          DeclarativeConfiguration.toConfigProperties(model));
    }
  }

  private static void logStart() {
    logger.info(
        "OpenTelemetry Spring Boot starter ({}) has been started",
        EmbeddedInstrumentationProperties.findVersion(
            "io.opentelemetry.spring-boot-autoconfigure"));
  }

  @Configuration
  @ConditionalOnMissingBean(OpenTelemetry.class)
  @ConditionalOnProperty(name = "otel.sdk.disabled", havingValue = "true")
  static class DisabledOpenTelemetrySdkConfig {

    @Bean
    public OpenTelemetry openTelemetry() {
      logger.info("OpenTelemetry Spring Boot starter has been disabled");

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
