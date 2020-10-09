/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.jsp;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.HttpJspPage;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.compiler.Compiler;
import org.slf4j.LoggerFactory;

public class JSPTracer extends BaseTracer {
  public static final JSPTracer TRACER = new JSPTracer();

  public String spanNameOnCompile(JspCompilationContext jspCompilationContext) {
    return jspCompilationContext == null
        ? "Compile"
        : "Compile " + jspCompilationContext.getJspFile();
  }

  public void onCompile(Span span, JspCompilationContext jspCompilationContext) {
    if (jspCompilationContext != null) {
      ServletContext servletContext = jspCompilationContext.getServletContext();
      if (servletContext != null) {
        span.setAttribute("servlet.context", servletContext.getContextPath());
      }

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

  public void onRender(Span span, HttpServletRequest req) {
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
          "jsp.requestURL", (new URI(req.getRequestURL().toString())).normalize().toString());
    } catch (URISyntaxException uriSE) {
      LoggerFactory.getLogger(HttpJspPage.class)
          .warn("Failed to get and normalize request URL: " + uriSE.getMessage());
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.jsp-2.3";
  }
}
