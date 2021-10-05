/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.HttpService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaTracing;
import io.opentelemetry.instrumentation.armeria.v1_3.internal.ArmeriaNetClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import java.util.function.Function;

// Holds singleton references to decorators to match against during suppression.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/903
public final class ArmeriaSingletons {
  public static final Function<? super HttpClient, ? extends HttpClient> CLIENT_DECORATOR;

  public static final Function<? super HttpService, ? extends HttpService> SERVER_DECORATOR;

  static {
    // TODO (trask) split ArmeriaNetAttributesExtractor into client/server
    //  (and move into ArmeriaTracingBuilder?)
    PeerServiceAttributesExtractor<RequestContext, RequestLog> peerServiceAttributesExtractor =
        PeerServiceAttributesExtractor.create(new ArmeriaNetClientAttributesExtractor());

    ArmeriaTracing tracing =
        ArmeriaTracing.newBuilder(GlobalOpenTelemetry.get())
            .addAttributeExtractor(peerServiceAttributesExtractor)
            .build();

    CLIENT_DECORATOR = tracing.newClientDecorator();
    SERVER_DECORATOR = tracing.newServiceDecorator();
  }

  private ArmeriaSingletons() {}
}
