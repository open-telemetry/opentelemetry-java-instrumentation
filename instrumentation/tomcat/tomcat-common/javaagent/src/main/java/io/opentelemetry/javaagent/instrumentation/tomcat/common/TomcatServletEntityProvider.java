/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import org.apache.coyote.Request;
import org.apache.coyote.Response;

/**
 * Used to access servlet request/response classes from their Apache Coyote counterparts. As the
 * Coyote classes are the same for all Tomcat versions, but newer Tomcat uses jakarta.servlet
 * instead of javax.servlet, this allows accessing the servlet entities without unchecked casts in
 * shared code where HttpServletRequest and/or HttpServletResponse are used as generic parameters.
 *
 * @param <REQUEST> HttpServletRequest instance
 * @param <RESPONSE> HttpServletResponse instance
 */
public interface TomcatServletEntityProvider<REQUEST, RESPONSE> {
  REQUEST getServletRequest(Request request);

  RESPONSE getServletResponse(Response response);
}
