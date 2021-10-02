/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.HttpJspPage;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.LoggerFactory;

public class HttpJspPageInstrumentationSingletons {
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.jsp.experimental-span-attributes", false);

  private static final Instrumenter<HttpServletRequest, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<HttpServletRequest, Void>newBuilder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.jsp-2.3",
                HttpJspPageInstrumentationSingletons::spanNameOnRender)
            .addAttributesExtractor(new RenderAttributesExtractor())
            .newInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  private static String spanNameOnRender(HttpServletRequest req) {
    // get the JSP file name being rendered in an include action
    Object includeServletPath = req.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
    String spanName = req.getServletPath();
    if (includeServletPath instanceof String) {
      spanName = includeServletPath.toString();
    }
    return "Render " + spanName;
  }

  public static Instrumenter<HttpServletRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private HttpJspPageInstrumentationSingletons() {}

  private static class RenderAttributesExtractor
      extends AttributesExtractor<HttpServletRequest, Void> {

    @Override
    protected void onStart(AttributesBuilder attributes, HttpServletRequest request) {
      if (!CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
        return;
      }

      Object forwardOrigin = request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
      if (forwardOrigin instanceof String) {
        attributes.put("jsp.forwardOrigin", forwardOrigin.toString());
      }

      // add the request URL as a tag to provide better context when looking at spans produced by
      // actions. Tomcat 9 has relative path symbols in the value returned from
      // HttpServletRequest#getRequestURL(),
      // normalizing the URL should remove those symbols for readability and consistency
      try {
        attributes.put(
            "jsp.requestURL", new URI(request.getRequestURL().toString()).normalize().toString());
      } catch (URISyntaxException e) {
        LoggerFactory.getLogger(HttpJspPage.class)
            .warn("Failed to get and normalize request URL: " + e.getMessage());
      }
    }

    @Override
    protected void onEnd(
        AttributesBuilder attributes,
        HttpServletRequest httpServletRequest,
        @Nullable Void unused,
        @Nullable Throwable error) {}
  }
}
