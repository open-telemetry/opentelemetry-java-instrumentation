/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.checkerframework.checker.nullness.qual.Nullable;

final class StatusCodeExtractor
    extends AttributesExtractor<HttpServletRequest, HttpServletResponse> {

  @Override
  protected void onStart(AttributesBuilder attributes, HttpServletRequest httpServletRequest) {}

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      HttpServletRequest httpServletRequest,
      @Nullable HttpServletResponse response,
      @Nullable Throwable error) {
    if (response != null) {
      long statusCode;
      // if response is not committed and there is a throwable set status to 500 /
      // INTERNAL_SERVER_ERROR, due to servlet spec
      // https://javaee.github.io/servlet-spec/downloads/servlet-4.0/servlet-4_0_FINAL.pdf:
      // "If a servlet generates an error that is not handled by the error page mechanism as
      // described above, the container must ensure to send a response with status 500."
      if (!response.isCommitted() && error != null) {
        statusCode = 500;
      } else {
        statusCode = response.getStatus();
      }

      set(attributes, SemanticAttributes.HTTP_STATUS_CODE, statusCode);
    }
  }
}
