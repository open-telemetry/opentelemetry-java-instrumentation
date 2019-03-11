package datadog.trace.instrumentation.jsp;

import datadog.trace.agent.decorator.BaseDecorator;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.HttpJspPage;
import org.apache.jasper.JspCompilationContext;
import org.slf4j.LoggerFactory;

public class JSPDecorator extends BaseDecorator {
  public static JSPDecorator DECORATE = new JSPDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"jsp"};
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "jsp-http-servlet";
  }

  public void onCompile(final Scope scope, final JspCompilationContext jspCompilationContext) {
    if (jspCompilationContext != null) {
      final Span span = scope.span();
      span.setTag(DDTags.RESOURCE_NAME, jspCompilationContext.getJspFile());

      if (jspCompilationContext.getServletContext() != null) {
        span.setTag("servlet.context", jspCompilationContext.getServletContext().getContextPath());
      }

      if (jspCompilationContext.getCompiler() != null) {
        span.setTag("jsp.compiler", jspCompilationContext.getCompiler().getClass().getName());
      }
      span.setTag("jsp.classFQCN", jspCompilationContext.getFQCN());
    }
  }

  public void onRender(final Scope scope, final HttpServletRequest req) {
    final Span span = scope.span();
    // get the JSP file name being rendered in an include action
    final Object includeServletPath = req.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
    String resourceName = req.getServletPath();
    if (includeServletPath instanceof String) {
      resourceName = includeServletPath.toString();
    }
    span.setTag(DDTags.RESOURCE_NAME, resourceName);

    final Object forwardOrigin = req.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
    if (forwardOrigin instanceof String) {
      span.setTag("jsp.forwardOrigin", forwardOrigin.toString());
    }

    // add the request URL as a tag to provide better context when looking at spans produced by
    // actions. Tomcat 9 has relative path symbols in the value returned from
    // HttpServletRequest#getRequestURL(),
    // normalizing the URL should remove those symbols for readability and consistency
    try {
      span.setTag(
          "jsp.requestURL", (new URI(req.getRequestURL().toString())).normalize().toString());
    } catch (final URISyntaxException uriSE) {
      LoggerFactory.getLogger(HttpJspPage.class)
          .warn("Failed to get and normalize request URL: " + uriSE.getMessage());
    }
  }
}
