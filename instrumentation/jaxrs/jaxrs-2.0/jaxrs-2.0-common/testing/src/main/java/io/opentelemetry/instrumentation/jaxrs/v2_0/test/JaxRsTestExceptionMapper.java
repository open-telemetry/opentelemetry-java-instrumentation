/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v2_0.test;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class JaxRsTestExceptionMapper implements ExceptionMapper<Exception> {
  @Override
  public Response toResponse(Exception exception) {
    return Response.status(500).entity(exception.getMessage()).build();
  }
}
