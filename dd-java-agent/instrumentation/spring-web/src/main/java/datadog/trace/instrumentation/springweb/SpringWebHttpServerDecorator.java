package datadog.trace.instrumentation.springweb;

import datadog.trace.agent.decorator.HttpServerDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

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
  public Span onRequest(final Span span, final HttpServletRequest request) {
    super.onRequest(span, request);
    if (request != null) {
      final String method = request.getMethod();
      final Object bestMatchingPattern =
          request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (method != null && bestMatchingPattern != null) {
        final String resourceName = method + " " + bestMatchingPattern;
        span.setTag(DDTags.RESOURCE_NAME, resourceName);
        span.setTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_SERVER);
      }
    }
    return span;
  }

  public Scope onRender(final Scope scope, final ModelAndView mv) {
    final Span span = scope.span();
    if (mv.getViewName() != null) {
      span.setTag("view.name", mv.getViewName());
    }
    if (mv.getView() != null) {
      span.setTag("view.type", mv.getView().getClass().getName());
    }
    return scope;
  }
}
