package datadog.trace.instrumentation.jsp;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.HttpJspPage;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.LoggerFactory;

@AutoService(Instrumenter.class)
public final class JSPInstrumentation extends Instrumenter.Default {

  public JSPInstrumentation() {
    super("jsp", "jsp-render");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.servlet.jsp.HttpJspPage")));
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        named("_jspService")
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
            .and(isPublic()),
        HttpJspPageAdvice.class.getName());
    return transformers;
  }

  public static class HttpJspPageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.This final Object obj, @Advice.Argument(0) final HttpServletRequest req) {
      final Scope scope =
          GlobalTracer.get()
              .buildSpan("jsp.render")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET)
              .withTag("span.origin.type", obj.getClass().getSimpleName())
              .withTag("servlet.context", req.getContextPath())
              .startActive(true);

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

      Tags.COMPONENT.set(span, "jsp-http-servlet");

      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(1) final HttpServletResponse resp,
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable) {

      final Span span = scope.span();
      if (throwable != null) {
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
      scope.close();
    }
  }
}
