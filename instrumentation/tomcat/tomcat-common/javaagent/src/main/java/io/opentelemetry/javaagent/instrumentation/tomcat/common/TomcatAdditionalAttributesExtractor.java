/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.security.Principal;
import javax.annotation.Nullable;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

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
  public void onStart(AttributesBuilder attributes, Context parentContext, Request request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      Request request,
      @Nullable Response response,
      @Nullable Throwable error) {
    REQUEST servletRequest = servletEntityProvider.getServletRequest(request);
    Principal principal = accessor.getRequestUserPrincipal(servletRequest);
    if (principal != null) {
      String name = principal.getName();
      if (name != null) {
        attributes.put(SemanticAttributes.ENDUSER_ID, name);
      }
    }
  }
}
