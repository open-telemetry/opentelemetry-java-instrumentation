/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import static io.opentelemetry.api.common.AttributeKey.longKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes;
import java.security.Principal;
import javax.annotation.Nullable;

public class ServletAdditionalAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.servlet.experimental-span-attributes", false);
  private static final AttributeKey<Long> SERVLET_TIMEOUT = longKey("servlet.timeout");

  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletAdditionalAttributesExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ServletRequestContext<REQUEST> requestContext) {}

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext,
      @Nullable Throwable error) {
    if (AgentCommonConfig.get().getEnduserConfig().isIdEnabled()) {
      Principal principal = accessor.getRequestUserPrincipal(requestContext.request());
      if (principal != null) {
        String name = principal.getName();
        if (name != null) {
          attributes.put(EnduserIncubatingAttributes.ENDUSER_ID, name);
        }
      }
    }
    if (!CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      return;
    }
    if (responseContext != null && responseContext.hasTimeout()) {
      attributes.put(SERVLET_TIMEOUT, responseContext.getTimeout());
    }
  }
}
