/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletHttpAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletResponseContext;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet2HttpAttributesExtractor
    extends ServletHttpAttributesExtractor<HttpServletRequest, HttpServletResponse> {
  public Servlet2HttpAttributesExtractor(
      ServletAccessor<HttpServletRequest, HttpServletResponse> accessor) {
    super(accessor);
  }

  @Override
  @Nullable
  protected Integer statusCode(
      ServletRequestContext<HttpServletRequest> requestContext,
      ServletResponseContext<HttpServletResponse> responseContext) {
    HttpServletResponse response = responseContext.response();

    if (!accessor.isResponseCommitted(response) && responseContext.error() != null) {
      // if response is not committed and there is a throwable set status to 500 /
      // INTERNAL_SERVER_ERROR, due to servlet spec
      // https://javaee.github.io/servlet-spec/downloads/servlet-4.0/servlet-4_0_FINAL.pdf:
      // "If a servlet generates an error that is not handled by the error page mechanism as
      // described above, the container must ensure to send a response with status 500."
      return 500;
    }
    if (responseContext.hasStatus()) {
      return responseContext.getStatus();
    }
    return null;
  }
}
