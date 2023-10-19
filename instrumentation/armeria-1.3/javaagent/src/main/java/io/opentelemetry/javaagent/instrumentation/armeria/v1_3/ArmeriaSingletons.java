/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.server.HttpService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaTelemetry;
import io.opentelemetry.instrumentation.armeria.v1_3.internal.ArmeriaHttpClientAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import java.util.function.Function;

// Holds singleton references to decorators to match against during suppression.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/903
public final class ArmeriaSingletons {
  public static final Function<? super HttpClient, ? extends HttpClient> CLIENT_DECORATOR;

  public static final Function<? super HttpService, ? extends HttpService> SERVER_DECORATOR;

  static {
    ArmeriaTelemetry telemetry =
        ArmeriaTelemetry.builder(GlobalOpenTelemetry.get())
            .setCapturedClientRequestHeaders(CommonConfig.get().getClientRequestHeaders())
            .setCapturedClientResponseHeaders(CommonConfig.get().getClientResponseHeaders())
            .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
            .addClientAttributeExtractor(
                HttpClientPeerServiceAttributesExtractor.create(
                    ArmeriaHttpClientAttributesGetter.INSTANCE,
                    CommonConfig.get().getPeerServiceResolver()))
            .setEmitExperimentalHttpClientMetrics(
                CommonConfig.get().shouldEmitExperimentalHttpClientMetrics())
            .setEmitExperimentalHttpServerMetrics(
                CommonConfig.get().shouldEmitExperimentalHttpServerMetrics())
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
