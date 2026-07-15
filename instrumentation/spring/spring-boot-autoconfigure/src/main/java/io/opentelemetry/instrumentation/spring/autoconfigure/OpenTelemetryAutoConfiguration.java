/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedConfigProvider;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigPropertiesBridgeBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.DeclarativeConfigDisabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.DeclarativeConfigEnabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelDisabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelEnabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelMapConverter;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread.ThreadDetailsCustomizerProvider;
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
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.internal.SpiHelper;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
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
      ResourceProvider otelSpringResourceProvider(Optional<BuildProperties> buildProperties) {
        return new SpringResourceProvider(buildProperties);
      }

      @Bean
      ResourceProvider otelDistroVersionResourceProvider() {
        return new DistroVersionResourceProvider();
      }

      @Bean
      @ConfigurationPropertiesBinding
      OtelMapConverter otelMapConverter() {
        return new OtelMapConverter();
      }

      /**
       * Backs the {@link ConfigProvider} and {@link OpenTelemetry} beans below; not meant to be
       * injected directly.
       */
      @Bean
      AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk(
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

      /**
       * Bridges the legacy {@link ConfigProperties}-based configuration into a {@link
       * ConfigProvider}, since instrumentation only reads config through {@link ConfigProvider}.
       */
      @Bean
      ConfigProvider configProvider(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
        return ConfigPropertiesBackedConfigProvider.create(
            requireNonNull(AutoConfigureUtil.getConfig(autoConfiguredOpenTelemetrySdk)));
      }

      @Bean
      OpenTelemetry openTelemetry(
          AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk,
          ConfigProvider configProvider) {
        logStart();
        OpenTelemetrySdk openTelemetry = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk();
        return new SpringOpenTelemetrySdk(openTelemetry, configProvider);
      }

      /**
       * Expose the {@link ConfigProperties} bean for use in other auto-configurations.
       *
       * <p>Not using spring boot properties directly in order to support {@link
       * io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer#addPropertiesCustomizer(Function)}
       * and {@link
       * io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer#addPropertiesSupplier(Supplier)}.
       *
       * @deprecated use {@link ConfigProvider} instead
       */
      @Deprecated // to be removed in 3.0
      @Bean
      ConfigProperties otelProperties(
          AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
        return requireNonNull(AutoConfigureUtil.getConfig(autoConfiguredOpenTelemetrySdk));
      }
    }

    @Configuration
    @Conditional(DeclarativeConfigEnabled.class)
    static class EmbeddedConfigFileConfig {

      /** Backs the {@link ConfigProvider} bean below; not meant to be injected directly. */
      @Bean
      OpenTelemetryConfigurationModel openTelemetryConfigurationModel(
          ConfigurableEnvironment environment) {
        return EmbeddedConfigFile.extractModel(environment);
      }

      /**
       * Unlike {@link
       * OpenTelemetrySdkConfig.PropertiesConfig#configProvider(AutoConfiguredOpenTelemetrySdk)},
       * this derives the provider from the constructed {@link OpenTelemetry} so that it reflects
       * declarative model customizers applied during SDK creation.
       *
       * <p>This mirrors how the SDK itself builds its {@code ConfigProvider}: <a
       * href="https://github.com/open-telemetry/opentelemetry-java/blob/83f947f97fb0961178072b413c07a3689488ef34/sdk-extensions/declarative-config/src/main/java/io/opentelemetry/sdk/autoconfigure/declarativeconfig/OpenTelemetryConfigurationFactory.java#L45">OpenTelemetryConfigurationFactory#create</a>
       * converts the (already-customized) model to properties and hands the resulting {@code
       * ConfigProvider} to the SDK builder, so the built {@link OpenTelemetry} is the only place
       * where the post-customization config is available.
       */
      @Bean
      ConfigProvider configProvider(OpenTelemetry openTelemetry) {
        return configProviderFrom(openTelemetry);
      }

      @Bean
      OpenTelemetry openTelemetry(
          OpenTelemetryConfigurationModel model, ApplicationContext applicationContext) {
        OpenTelemetrySdkComponentLoader componentLoader =
            new OpenTelemetrySdkComponentLoader(applicationContext);

        OpenTelemetrySdk sdk =
            DeclarativeConfiguration.create(model, componentLoader).getSdk();
        SpringConfigProvider configProvider =
            SpringConfigProvider.create(configProviderFrom(sdk).getInstrumentationConfig());
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));
        logStart();
        return new SpringOpenTelemetrySdk(sdk, configProvider);
      }

      /**
       * Expose the {@link ConfigProperties} bean for use in other auto-configurations.
       *
       * <p>Not using spring boot properties directly, because declarative configuration does not
       * integrate with spring boot properties.
       *
       * @deprecated Migrate consuming auto-configurations to the Declarative Config API. This
       *     compatibility bean will be removed in 3.0 together with {@link
       *     DeclarativeConfigPropertiesBridgeBuilder}.
       */
      @Deprecated // will be removed in 3.0
      @Bean
      ConfigProperties otelProperties(OpenTelemetry openTelemetry) {
        return new DeclarativeConfigPropertiesBridgeBuilder()
            .buildFromInstrumentationConfig(
                configProviderFrom(openTelemetry).getInstrumentationConfig());
      }

      private static ConfigProvider configProviderFrom(OpenTelemetry openTelemetry) {
        if (openTelemetry instanceof ExtendedOpenTelemetry) {
          return ((ExtendedOpenTelemetry) openTelemetry).getConfigProvider();
        }
        return ConfigProvider.noop();
      }

      @Bean
      DeclarativeConfigurationCustomizerProvider distroConfigurationCustomizerProvider() {
        return new ResourceCustomizerProvider();
      }

      @Bean
      DeclarativeConfigurationCustomizerProvider threadDetailsCustomizerProvider() {
        return new ThreadDetailsCustomizerProvider();
      }

      @Bean
      ComponentProvider distroComponentProvider() {
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
    OpenTelemetry openTelemetry() {
      logger.info("OpenTelemetry Spring Boot starter has been disabled");

      return OpenTelemetry.noop();
    }

    /**
     * @deprecated use {@link ConfigProvider} instead
     */
    @Deprecated // to be removed in 3.0
    @Bean
    ConfigProperties otelProperties() {
      return DefaultConfigProperties.createFromMap(emptyMap());
    }

    @Bean
    ConfigProvider configProvider() {
      return ConfigProvider.noop();
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

  /**
   * @deprecated use {@link ConfigProvider} from {@link FallbackConfigProvider} instead
   */
  @Deprecated // to be removed in 3.0
  @Configuration
  @ConditionalOnBean(OpenTelemetry.class)
  @ConditionalOnMissingBean({ConfigProperties.class})
  static class FallbackConfigProperties {
    @Bean
    ConfigProperties otelProperties(ApplicationContext applicationContext) {
      return DefaultConfigProperties.create(
          emptyMap(), new OpenTelemetrySdkComponentLoader(applicationContext));
    }
  }

  /**
   * Backstops setups where a custom {@link OpenTelemetry} bean is supplied (so {@link
   * OpenTelemetrySdkConfig} doesn't apply) and no {@link ConfigProvider} bean is otherwise defined,
   * so that instrumentation can always rely on a {@link ConfigProvider} bean being present.
   */
  @Configuration
  @ConditionalOnBean(OpenTelemetry.class)
  @ConditionalOnMissingBean({ConfigProvider.class})
  static class FallbackConfigProvider {
    @Bean
    ConfigProvider configProvider() {
      return ConfigProvider.noop();
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
      List<T> spi = new ArrayList<>(spiHelper.load(spiClass));
      List<T> beans =
          applicationContext.getBeanProvider(spiClass).orderedStream().collect(toList());
      spi.addAll(beans);
      return spi;
    }
  }
}
