/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigPropertiesBridgeBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.DeclarativeConfigDisabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.DeclarativeConfigEnabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelDisabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelEnabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelMapConverter;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtelResourceProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtelSpringProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtlpExporterProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.SpringConfigProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.resources.DistroComponentProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.resources.DistroVersionResourceProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.resources.ResourceCustomizerProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.resources.SpringResourceProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
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
  @Conditional(OtelEnabled.class)
  @ConditionalOnMissingBean(OpenTelemetry.class)
  static class OpenTelemetrySdkConfig {

    public OpenTelemetrySdkConfig() {}

    @Configuration
    @EnableConfigurationProperties({
      OtlpExporterProperties.class,
      OtelResourceProperties.class,
      OtelSpringProperties.class
    })
    @Conditional(DeclarativeConfigDisabled.class)
    static class PropertiesConfig {

      @Bean
      public ResourceProvider otelSpringResourceProvider(
          Optional<BuildProperties> buildProperties) {
        return new SpringResourceProvider(buildProperties);
      }

      @Bean
      public ResourceProvider otelDistroVersionResourceProvider() {
        return new DistroVersionResourceProvider();
      }

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

        return AutoConfigureUtil.setConfigPropertiesCustomizer(
                AutoConfiguredOpenTelemetrySdk.builder()
                    .setComponentLoader(new OpenTelemetrySdkComponentLoader(applicationContext)),
                c ->
                    SpringConfigProperties.create(
                        env, otlpExporterProperties, resourceProperties, otelSpringProperties, c))
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
        return requireNonNull(AutoConfigureUtil.getConfig(autoConfiguredOpenTelemetrySdk));
      }
    }

    @Configuration
    @Conditional(DeclarativeConfigEnabled.class)
    static class EmbeddedConfigFileConfig {

      @Bean
      public OpenTelemetryConfigurationModel openTelemetryConfigurationModel(
          ConfigurableEnvironment environment) throws IOException {
        return new EmbeddedConfigFile(environment).extractModel(environment);
      }

      @Bean
      public OpenTelemetry openTelemetry(
          OpenTelemetryConfigurationModel model, ApplicationContext applicationContext) {
        OpenTelemetrySdk sdk =
            DeclarativeConfiguration.create(
                model, new OpenTelemetrySdkComponentLoader(applicationContext));
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));
        logStart();
        return sdk;
      }

      @Bean
      public ConfigProvider configProvider(OpenTelemetryConfigurationModel model) {
        return SpringConfigProvider.create(model);
      }

      /**
       * Expose the {@link ConfigProperties} bean for use in other auto-configurations.
       *
       * <p>Not using spring boot properties directly, because declarative configuration does not
       * integrate with spring boot properties.
       */
      @Bean
      public ConfigProperties otelProperties(ConfigProvider configProvider) {
        return new DeclarativeConfigPropertiesBridgeBuilder()
            .buildFromInstrumentationConfig(configProvider.getInstrumentationConfig());
      }

      @Bean
      public InstrumentationConfig instrumentationConfig(
          ConfigProperties properties, ConfigProvider configProvider) {
        return new ConfigPropertiesBridge(properties, configProvider);
      }

      @Bean
      public DeclarativeConfigurationCustomizerProvider distroConfigurationCustomizerProvider() {
        return new ResourceCustomizerProvider();
      }

      @Bean
      public ComponentProvider distroComponentProvider() {
        return new DistroComponentProvider();
      }
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
  @Conditional(OtelDisabled.class)
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

    @Bean
    public InstrumentationConfig instrumentationConfig(ConfigProperties properties) {
      return new ConfigPropertiesBridge(properties, null);
    }

    @Configuration
    @Conditional(DeclarativeConfigDisabled.class)
    static class PropertiesConfig {
      /**
       * Is only added so that we have the same converters as with active OpenTelemetry SDK
       *
       * <p>In other words, don't break applications that (accidentally) use the {@link
       * OtelMapConverter}.
       */
      @Bean
      OtelMapConverter otelMapConverter() {
        return new OtelMapConverter();
      }
    }
  }

  @Configuration
  @ConditionalOnBean(OpenTelemetry.class)
  @ConditionalOnMissingBean({ConfigProperties.class})
  static class FallbackConfigProperties {
    @Bean
    public ConfigProperties otelProperties(ApplicationContext applicationContext) {
      return DefaultConfigProperties.create(
          Collections.emptyMap(), new OpenTelemetrySdkComponentLoader(applicationContext));
    }
  }

  @Configuration
  @ConditionalOnBean(OpenTelemetry.class)
  @ConditionalOnMissingBean({InstrumentationConfig.class})
  static class FallbackInstrumentationConfig {
    @Bean
    public InstrumentationConfig instrumentationConfig(ConfigProperties properties) {
      return new ConfigPropertiesBridge(properties, null);
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
