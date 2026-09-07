/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTargets;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpHost;
import org.opensearch.client.Node;
import org.opensearch.client.RestClient;

// Preserve the nodes configured at construction for telemetry. Automatic node discovery or
// setNodes() can later replace the client's active node list.
class RestClientConstructorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.opensearch.client.RestClient");
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
        @Advice.This RestClient restClient, @Advice.Argument(2) List<Node> configuredNodes) {
      List<OpenSearchServerTarget.Endpoint> endpoints = new ArrayList<>(configuredNodes.size());
      for (Node node : configuredNodes) {
        HttpHost host = node.getHost();
        endpoints.add(
            new OpenSearchServerTarget.Endpoint(
                host.getHostName(), host.getPort(), host.getSchemeName()));
      }
      OpenSearchServerTargets.capture(restClient, endpoints);
    }
  }
}
