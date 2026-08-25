/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v5_0;

import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0.ElasticsearchServerTargets;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

/**
 * Records the target a rest client was built with. {@code RestClient.setHosts} replaces the hosts a
 * client routes to afterwards, and the configured target must not follow it.
 */
class RestClientConstructorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.elasticsearch.client.RestClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(3, named("org.apache.http.HttpHost[]"))),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RestClient restClient, @Advice.Argument(3) HttpHost[] configuredHosts) {
      ElasticsearchServerTargets.capture(restClient, asList(configuredHosts));
    }
  }
}
