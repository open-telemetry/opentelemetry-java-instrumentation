/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.JaxRsClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import org.glassfish.jersey.client.ClientRequest;

public final class JerseyClientUtil {

  public static void handleException(ClientRequest context, Throwable exception) {
    Object prop = context.getProperty(ClientTracingFilter.CONTEXT_PROPERTY_NAME);
    if (prop instanceof Context) {
      instrumenter().end((Context) prop, context, null, exception);
    }
  }

  private JerseyClientUtil() {}
}
