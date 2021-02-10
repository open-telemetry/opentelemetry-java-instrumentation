/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.JaxRsClientTracer.tracer;

import io.opentelemetry.context.Context;
import javax.ws.rs.client.ClientRequestContext;
import org.apache.cxf.jaxrs.client.spec.ClientRequestContextImpl;
import org.apache.cxf.message.Message;

public final class CxfClientUtil {

  public static void handleException(Message message, Throwable throwable) {
    ClientRequestContext context = new ClientRequestContextImpl(message, false);
    Object prop = context.getProperty(ClientTracingFilter.CONTEXT_PROPERTY_NAME);
    if (prop instanceof Context) {
      tracer().endExceptionally((Context) prop, throwable);
    }
  }

  private CxfClientUtil() {}
}
