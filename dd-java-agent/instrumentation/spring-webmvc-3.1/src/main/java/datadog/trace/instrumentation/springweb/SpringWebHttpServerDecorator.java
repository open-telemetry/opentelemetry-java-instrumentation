package datadog.trace.instrumentation.springweb;

import datadog.trace.agent.decorator.HttpServerDecorator;
import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.api.AgentSpan;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

@Slf4j
public class SpringWebHttpServerDecorator
    extends HttpServerDecorator<HttpServletRequest, HttpServletRequest, HttpServletResponse> {
  public static final SpringWebHttpServerDecorator DECORATE = new SpringWebHttpServerDecorator();
  public static final SpringWebHttpServerDecorator DECORATE_RENDER =
      new SpringWebHttpServerDecorator() {
        @Override
        protected String component() {
          return "spring-webmvc";
        }
      };

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"spring-web"};
  }

  @Override
  protected String component() {
    return "spring-web-controller";
  }

  @Override
  protected boolean traceAnalyticsDefault() {
    return false;
  }

  @Override
  protected String method(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getMethod();
  }

  @Override
  protected URI url(final HttpServletRequest httpServletRequest) throws URISyntaxException {
    return new URI(httpServletRequest.getRequestURL().toString());
  }

  @Override
  protected String peerHostname(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemoteHost();
  }

  @Override
  protected String peerHostIP(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemoteAddr();
  }

  @Override
  protected Integer peerPort(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemotePort();
  }

  @Override
  protected Integer status(final HttpServletResponse httpServletResponse) {
    return httpServletResponse.getStatus();
  }

  @Override
  public AgentSpan onRequest(final AgentSpan span, final HttpServletRequest request) {
    if (request != null) {
      final String method = request.getMethod();
      final Object bestMatchingPattern =
          request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (method != null && bestMatchingPattern != null) {
        final String resourceName = method + " " + bestMatchingPattern;
        span.setTag(DDTags.RESOURCE_NAME, resourceName);
      }
    }
    return span;
  }

  public void onHandle(final AgentSpan span, final Object handler) {
    final Class<?> clazz;
    final String methodName;

    if (handler instanceof HandlerMethod) {
      // name span based on the class and method name defined in the handler
      final Method method = ((HandlerMethod) handler).getMethod();
      clazz = method.getDeclaringClass();
      methodName = method.getName();
    } else if (handler instanceof HttpRequestHandler) {
      // org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter
      clazz = handler.getClass();
      methodName = "handleRequest";
    } else if (handler instanceof Controller) {
      // org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter
      clazz = handler.getClass();
      methodName = "handleRequest";
    } else if (handler instanceof Servlet) {
      // org.springframework.web.servlet.handler.SimpleServletHandlerAdapter
      clazz = handler.getClass();
      methodName = "service";
    } else {
      // perhaps org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter
      clazz = handler.getClass();
      methodName = "<annotation>";
    }

    final String resourceName = DECORATE.spanNameForClass(clazz) + "." + methodName;
    span.setTag(DDTags.RESOURCE_NAME, resourceName);
  }

  public AgentSpan onRender(final AgentSpan span, final ModelAndView mv) {
    final String viewName = mv.getViewName();
    if (viewName != null) {
      span.setTag("view.name", viewName);
      span.setTag(DDTags.RESOURCE_NAME, viewName);
    }
    if (mv.getView() != null) {
      span.setTag("view.type", mv.getView().getClass().getName());
    }
    return span;
  }
}
