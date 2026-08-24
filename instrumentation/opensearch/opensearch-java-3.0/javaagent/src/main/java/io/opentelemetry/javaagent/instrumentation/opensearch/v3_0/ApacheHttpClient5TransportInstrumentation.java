/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.internal.Node;

/**
 * Records the target an Apache HTTP Client 5 transport was built with. The transport replaces the
 * nodes it routes to as they fail and recover, and the configured target must not follow them.
 */
class ApacheHttpClient5TransportInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(2, named("java.util.List"))),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This OpenSearchTransport transport,
        @Advice.Argument(2) List<Node> configuredNodes) {
      List<OpenSearchServerTarget.Endpoint> endpoints = new ArrayList<>(configuredNodes.size());
      for (Node node : configuredNodes) {
        HttpHost host = node.getHost();
        endpoints.add(
            new OpenSearchServerTarget.Endpoint(
                host.getSchemeName(), host.getHostName(), host.getPort()));
      }
      OpenSearchServerTargets.capture(transport, endpoints);
    }
  }
}
