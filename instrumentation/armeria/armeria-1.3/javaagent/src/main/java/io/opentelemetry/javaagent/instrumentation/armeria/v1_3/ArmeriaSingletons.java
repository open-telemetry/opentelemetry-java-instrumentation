/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.server.HttpService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaTelemetry;
import io.opentelemetry.instrumentation.armeria.v1_3.internal.ArmeriaHttpClientAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.function.Function;

// Holds singleton references to decorators to match against during suppression.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/903
public final class ArmeriaSingletons {
  public static final Function<? super HttpClient, ? extends HttpClient> CLIENT_DECORATOR;

  public static final Function<? super HttpService, ? extends HttpService> SERVER_DECORATOR;

  static {
    ArmeriaTelemetry telemetry =
        ArmeriaTelemetry.builder(GlobalOpenTelemetry.get())
            .setCapturedClientRequestHeaders(AgentCommonConfig.get().getClientRequestHeaders())
            .setCapturedClientResponseHeaders(AgentCommonConfig.get().getClientResponseHeaders())
            .setCapturedServerRequestHeaders(AgentCommonConfig.get().getServerRequestHeaders())
            .setCapturedServerResponseHeaders(AgentCommonConfig.get().getServerResponseHeaders())
            .setKnownMethods(AgentCommonConfig.get().getKnownHttpRequestMethods())
            .addClientAttributeExtractor(
                HttpClientPeerServiceAttributesExtractor.create(
                    ArmeriaHttpClientAttributesGetter.INSTANCE,
                    AgentCommonConfig.get().getPeerServiceResolver()))
            .setEmitExperimentalHttpClientMetrics(
                AgentCommonConfig.get().shouldEmitExperimentalHttpClientTelemetry())
            .setEmitExperimentalHttpServerMetrics(
                AgentCommonConfig.get().shouldEmitExperimentalHttpServerTelemetry())
            .build();

    CLIENT_DECORATOR = telemetry.newClientDecorator();
    Function<? super HttpService, ? extends HttpService> libraryDecorator =
        telemetry
            .newServiceDecorator()
            .compose(service -> new ResponseCustomizingDecorator(service));
    SERVER_DECORATOR = service -> new ServerDecorator(service, libraryDecorator.apply(service));
  }

  private ArmeriaSingletons() {}
}
