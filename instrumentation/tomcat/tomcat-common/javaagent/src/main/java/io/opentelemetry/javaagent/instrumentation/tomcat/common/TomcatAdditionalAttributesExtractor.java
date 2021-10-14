/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.security.Principal;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TomcatAdditionalAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<Request, Response> {
  private final ServletAccessor<REQUEST, RESPONSE> accessor;
  private final TomcatServletEntityProvider<REQUEST, RESPONSE> servletEntityProvider;

  public TomcatAdditionalAttributesExtractor(
      ServletAccessor<REQUEST, RESPONSE> accessor,
      TomcatServletEntityProvider<REQUEST, RESPONSE> servletEntityProvider) {
    this.accessor = accessor;
    this.servletEntityProvider = servletEntityProvider;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Request request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Request request,
      @Nullable Response response,
      @Nullable Throwable error) {
    REQUEST servletRequest = servletEntityProvider.getServletRequest(request);
    Principal principal = accessor.getRequestUserPrincipal(servletRequest);
    if (principal != null) {
      set(attributes, SemanticAttributes.ENDUSER_ID, principal.getName());
    }
  }
}
