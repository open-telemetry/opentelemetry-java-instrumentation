/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import io.opentelemetry.instrumentation.servlet.internal.ServletAccessor;
import io.opentelemetry.instrumentation.servlet.internal.ServletHttpAttributesGetter;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.internal.ServletResponseContext;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet2HttpAttributesGetter
    extends ServletHttpAttributesGetter<HttpServletRequest, HttpServletResponse> {

  public Servlet2HttpAttributesGetter(
      ServletAccessor<HttpServletRequest, HttpServletResponse> accessor) {
    super(accessor);
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      ServletRequestContext<HttpServletRequest> requestContext,
      ServletResponseContext<HttpServletResponse> responseContext,
      @Nullable Throwable error) {
    HttpServletResponse response = responseContext.response();

    if (!accessor.isResponseCommitted(response) && error != null) {
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
