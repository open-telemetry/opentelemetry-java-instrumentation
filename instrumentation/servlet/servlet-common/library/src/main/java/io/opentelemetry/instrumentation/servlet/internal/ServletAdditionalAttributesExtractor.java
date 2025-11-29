/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.internal;

import static io.opentelemetry.api.common.AttributeKey.longKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes;
import java.security.Principal;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ServletAdditionalAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {

  private static final AttributeKey<Long> SERVLET_TIMEOUT = longKey("servlet.timeout");

  private final ServletAccessor<REQUEST, RESPONSE> accessor;
  private final boolean captureExperimentalAttributes;
  private final boolean captureEnduserId;

  public ServletAdditionalAttributesExtractor(
      ServletAccessor<REQUEST, RESPONSE> accessor,
      boolean captureExperimentalAttributes,
      boolean captureEnduserId) {
    this.accessor = accessor;
    this.captureExperimentalAttributes = captureExperimentalAttributes;
    this.captureEnduserId = captureEnduserId;
  }

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ServletRequestContext<REQUEST> requestContext) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext,
      @Nullable Throwable error) {
    if (captureEnduserId) {
      Principal principal = accessor.getRequestUserPrincipal(requestContext.request());
      if (principal != null) {
        String name = principal.getName();
        if (name != null) {
          attributes.put(EnduserIncubatingAttributes.ENDUSER_ID, name);
        }
      }
    }
    if (!captureExperimentalAttributes) {
      return;
    }
    if (responseContext != null && responseContext.hasTimeout()) {
      attributes.put(SERVLET_TIMEOUT, responseContext.getTimeout());
    }
  }
}
