/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import static io.opentelemetry.api.common.AttributeKey.longKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.security.Principal;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ServletAdditionalAttributesExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.servlet.experimental-span-attributes", false);
  private static final AttributeKey<Long> SERVLET_TIMEOUT = longKey("servlet.timeout");

  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletAdditionalAttributesExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  protected void onStart(
      AttributesBuilder attributes, ServletRequestContext<REQUEST> requestContext) {}

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext,
      @Nullable Throwable error) {
    Principal principal = accessor.getRequestUserPrincipal(requestContext.request());
    if (principal != null) {
      set(attributes, SemanticAttributes.ENDUSER_ID, principal.getName());
    }
    if (!CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      return;
    }
    if (responseContext != null && responseContext.hasTimeout()) {
      set(attributes, SERVLET_TIMEOUT, responseContext.getTimeout());
    }
  }
}
