/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.HttpJspPage;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.compiler.Compiler;
import org.slf4j.LoggerFactory;

public class JspTracer extends BaseTracer {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.jsp.experimental-span-attributes", false);

  private static final JspTracer TRACER = new JspTracer();

  public static JspTracer tracer() {
    return TRACER;
  }

  public String spanNameOnCompile(JspCompilationContext jspCompilationContext) {
    return jspCompilationContext == null
        ? "Compile"
        : "Compile " + jspCompilationContext.getJspFile();
  }

  public void onCompile(Context context, JspCompilationContext jspCompilationContext) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES && jspCompilationContext != null) {
      Span span = Span.fromContext(context);
      Compiler compiler = jspCompilationContext.getCompiler();
      if (compiler != null) {
        span.setAttribute("jsp.compiler", compiler.getClass().getName());
      }
      span.setAttribute("jsp.classFQCN", jspCompilationContext.getFQCN());
    }
  }

  public String spanNameOnRender(HttpServletRequest req) {
    // get the JSP file name being rendered in an include action
    Object includeServletPath = req.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
    String spanName = req.getServletPath();
    if (includeServletPath instanceof String) {
      spanName = includeServletPath.toString();
    }
    return "Render " + spanName;
  }

  public void onRender(Context context, HttpServletRequest req) {
    if (!CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      return;
    }
    Span span = Span.fromContext(context);

    Object forwardOrigin = req.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
    if (forwardOrigin instanceof String) {
      span.setAttribute("jsp.forwardOrigin", forwardOrigin.toString());
    }

    // add the request URL as a tag to provide better context when looking at spans produced by
    // actions. Tomcat 9 has relative path symbols in the value returned from
    // HttpServletRequest#getRequestURL(),
    // normalizing the URL should remove those symbols for readability and consistency
    try {
      span.setAttribute(
          "jsp.requestURL", new URI(req.getRequestURL().toString()).normalize().toString());
    } catch (URISyntaxException e) {
      LoggerFactory.getLogger(HttpJspPage.class)
          .warn("Failed to get and normalize request URL: " + e.getMessage());
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.jsp-2.3";
  }
}
