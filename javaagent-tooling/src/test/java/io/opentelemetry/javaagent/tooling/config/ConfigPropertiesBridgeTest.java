/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ClientModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalGeneralInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalHttpInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalPeerInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.InstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ServerModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ServiceMappingModel;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * common config that is read from declarative config or config properties in a different way
 *
 * <p>cannot test CommonConfig in its own module, because there is no implementation of
 * InstrumentationConfig in that module
 */
class ConfigPropertiesBridgeTest {

  public static Stream<Arguments> emptyGeneralConfig() {
    OpenTelemetryConfigurationModel emptyModel =
        new OpenTelemetryConfigurationModel()
            .withAdditionalProperty("instrumentation/development", new InstrumentationModel());

    DefaultConfigProperties configProperties = DefaultConfigProperties.createFromMap(emptyMap());

    return Stream.of(
        Arguments.of(
            "config properties",
            new ConfigPropertiesBridge(
                configProperties, null, OpenTelemetry.noop())),
        Arguments.of(
            "declarative config",
            new ConfigPropertiesBridge(
                configProperties,
                SdkConfigProvider.create(emptyModel),
                OpenTelemetry.noop())));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("emptyGeneralConfig")
  void testEmptyGeneralConfig(String name, InstrumentationConfig config) {
    CommonConfig commonConfig = new CommonConfig(config);
    assertThat(commonConfig.getPeerServiceResolver()).extracting("mapping").isEqualTo(emptyMap());
    assertThat(commonConfig.getClientRequestHeaders()).isEmpty();
    assertThat(commonConfig.getServerRequestHeaders()).isEmpty();
    assertThat(commonConfig.getClientResponseHeaders()).isEmpty();
    assertThat(commonConfig.getServerResponseHeaders()).isEmpty();
  }

  public static Stream<Arguments> fullGeneralConfig() {
    InstrumentationModel model =
        new InstrumentationModel()
            .withGeneral(
                new ExperimentalGeneralInstrumentationModel()
                    .withPeer(
                        new ExperimentalPeerInstrumentationModel()
                            .withServiceMapping(
                                asList(
                                    new ServiceMappingModel()
                                        .withService("cats-service")
                                        .withPeer("1.2.3.4"),
                                    new ServiceMappingModel()
                                        .withService("dogs-api")
                                        .withPeer("dogs-abcdef123.serverlessapis.com"))))
                    .withHttp(
                        new ExperimentalHttpInstrumentationModel()
                            .withClient(
                                new ClientModel()
                                    .withRequestCapturedHeaders(asList("header1", "header2"))
                                    .withResponseCapturedHeaders(asList("header3", "header4")))
                            .withServer(
                                new ServerModel()
                                    .withRequestCapturedHeaders(asList("header5", "header6"))
                                    .withResponseCapturedHeaders(asList("header7", "header8")))));
    OpenTelemetryConfigurationModel emptyModel =
        new OpenTelemetryConfigurationModel()
            .withAdditionalProperty("instrumentation/development", model);

    DefaultConfigProperties configProperties =
        DefaultConfigProperties.createFromMap(getProperties());

    return Stream.of(
        Arguments.of(
            "config properties",
            new ConfigPropertiesBridge(
                configProperties, null, OpenTelemetry.noop())),
        Arguments.of(
            "declarative config",
            new ConfigPropertiesBridge(
                configProperties,
                SdkConfigProvider.create(emptyModel),
                OpenTelemetry.noop())));
  }

  private static Map<String, String> getProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put(
        "otel.instrumentation.common.peer-service-mapping",
        "1.2.3.4=cats-service,dogs-abcdef123.serverlessapis.com=dogs-api");
    properties.put("otel.instrumentation.http.client.capture-request-headers", "header1,header2");
    properties.put("otel.instrumentation.http.client.capture-response-headers", "header3,header4");
    properties.put("otel.instrumentation.http.server.capture-request-headers", "header5,header6");
    properties.put("otel.instrumentation.http.server.capture-response-headers", "header7,header8");
    return properties;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fullGeneralConfig")
  void testFullGeneralConfig(String name, InstrumentationConfig config) {
    CommonConfig commonConfig = new CommonConfig(config);
    assertThat(commonConfig.getPeerServiceResolver())
        .extracting("mapping", InstanceOfAssertFactories.MAP)
        .containsOnlyKeys("1.2.3.4", "dogs-abcdef123.serverlessapis.com");
    assertThat(commonConfig.getClientRequestHeaders()).containsExactly("header1", "header2");
    assertThat(commonConfig.getClientResponseHeaders()).containsExactly("header3", "header4");
    assertThat(commonConfig.getServerRequestHeaders()).containsExactly("header5", "header6");
    assertThat(commonConfig.getServerResponseHeaders()).containsExactly("header7", "header8");
  }
}
