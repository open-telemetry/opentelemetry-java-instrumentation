/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxrsPathUtil.normalizePath;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import java.util.Optional;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;

public class JerseyTracingUtil {

  public static void updateServerSpanName(Request request) {
    Context context = Context.current();
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    ContainerRequest containerRequest = (ContainerRequest) request;
    UriInfo uriInfo = containerRequest.getUriInfo();
    ExtendedUriInfo extendedUriInfo = (ExtendedUriInfo) uriInfo;
    Optional<String> name =
        extendedUriInfo.getMatchedTemplates().stream()
            .map((uriTemplate) -> normalizePath(uriTemplate.getTemplate()))
            .reduce((a, b) -> b + a);
    if (!name.isPresent()) {
      return;
    }

    serverSpan.updateName(
        ServletContextPath.prepend(context, JaxrsContextPath.prepend(context, name.get())));
    // mark span name as updated from controller to avoid JaxRsAnnotationsTracer updating it
    ServerSpanNaming.updateSource(context, ServerSpanNaming.Source.CONTROLLER);
  }
}
