/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.springframework.web.servlet;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource.CONTROLLER;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.javaagent.instrumentation.springwebmvc.SpringWebMvcServerSpanNaming;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.WebUtils;

public class OpenTelemetryHandlerMappingFilter implements Filter, Ordered {
  private static final String PATH_ATTRIBUTE = getRequestPathAttribute();
  private static final MethodHandle usesPathPatternsMh = getUsesPathPatternsMh();
  private static final MethodHandle parseAndCacheMh = parseAndCacheMh();

  private final HttpRouteGetter<HttpServletRequest> serverSpanName =
      (context, request) -> {
        Object previousValue = null;
        if (this.parseRequestPath && PATH_ATTRIBUTE != null) {
          previousValue = request.getAttribute(PATH_ATTRIBUTE);
          // sets new value for PATH_ATTRIBUTE of request
          parseAndCache(request);
        }
        try {
          if (findMapping(request)) {
            // Name the parent span based on the matching pattern
            // Let the parent span resource name be set with the attribute set in findMapping.
            return SpringWebMvcServerSpanNaming.SERVER_SPAN_NAME.get(context, request);
          }
        } finally {
          // mimic spring DispatcherServlet and restore the previous value of PATH_ATTRIBUTE
          if (this.parseRequestPath && PATH_ATTRIBUTE != null) {
            if (previousValue == null) {
              request.removeAttribute(PATH_ATTRIBUTE);
            } else {
              request.setAttribute(PATH_ATTRIBUTE, previousValue);
            }
          }
        }
        return null;
      };

  @Nullable private List<HandlerMapping> handlerMappings;
  private boolean parseRequestPath;

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper((HttpServletResponse) response);

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      filterChain.doFilter(request, wrapper);
    } finally {
      if (handlerMappings != null) {
        Context context = Context.current();
        Span span = Span.fromContext(context);
        Object internalRequest = extractAttr(request, "request");
        if (internalRequest != null) {
          byte[] postData = (byte[]) extractAttr(internalRequest, "postData");
          if (postData != null) {
            String post = new String(rtrim(postData), UTF_8);
            span.setAttribute("http.request.body", post);
          }
        }
        span.setAttribute("http.response.body", new String(wrapper.getContentAsByteArray(), "utf-8"));
        wrapper.copyBodyToResponse();
        HttpRouteHolder.updateHttpRoute(
            context, CONTROLLER, serverSpanName, (HttpServletRequest) request);
      }
    }
  }

  private static Object extractAttr(Object object, String attrName) {
    try {
      Class clz = object.getClass();
      Field field = clz.getDeclaredField(attrName);
      field.setAccessible(true);
      return field.get(object);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      return null;
    }
  }

  private static byte[] rtrim(byte[] array) {
    int notZeroLen = array.length;
    for (int i = array.length - 1; i >= 0; --i, notZeroLen--) {
      if (array[i] != 0) {
        break;
      }
    }

    if (notZeroLen != array.length) {
      array = Arrays.copyOf(array, notZeroLen);
    }

    return array;
  }

  @Override
  public void destroy() {}

  /**
   * When a HandlerMapping matches a request, it sets HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
   * as an attribute on the request. This attribute is read by SpringWebMvcDecorator.onRequest and
   * set as the resource name.
   */
  private boolean findMapping(HttpServletRequest request) {
    try {
      // handlerMapping already null-checked above
      for (HandlerMapping mapping : Objects.requireNonNull(handlerMappings)) {
        HandlerExecutionChain handler = mapping.getHandler(request);
        if (handler != null) {
          return true;
        }
      }
    } catch (Exception ignored) {
      // mapping.getHandler() threw exception.  Ignore
    }
    return false;
  }

  public void setHandlerMappings(List<HandlerMapping> mappings) {
    List<HandlerMapping> handlerMappings = new ArrayList<>();
    for (HandlerMapping mapping : mappings) {
      // Originally we ran findMapping at the very beginning of the request. This turned out to have
      // application-crashing side-effects with grails. That is why we don't add all HandlerMapping
      // classes here. Although now that we run findMapping after the request, and only when server
      // span name has not been updated by a controller, the probability of bad side-effects is much
      // reduced even if we did add all HandlerMapping classes here.
      if (mapping instanceof RequestMappingHandlerMapping) {
        handlerMappings.add(mapping);
        if (usePathPatterns(mapping)) {
          this.parseRequestPath = true;
        }
      }
    }
    if (!handlerMappings.isEmpty()) {
      this.handlerMappings = handlerMappings;
    }
  }

  @Override
  public int getOrder() {
    // Run after all HIGHEST_PRECEDENCE items
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }

  private static MethodHandle getUsesPathPatternsMh() {
    // Method added in spring 5.3
    try {
      return MethodHandles.lookup()
          .findVirtual(
              HandlerMapping.class, "usesPathPatterns", MethodType.methodType(boolean.class));
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      return null;
    }
  }

  private static boolean usePathPatterns(HandlerMapping handlerMapping) {
    if (usesPathPatternsMh == null) {
      return false;
    }
    try {
      return (boolean) usesPathPatternsMh.invoke(handlerMapping);
    } catch (Throwable throwable) {
      throw new IllegalStateException(throwable);
    }
  }

  private static MethodHandle parseAndCacheMh() {
    // ServletRequestPathUtils added in spring 5.3
    try {
      Class<?> pathUtilsClass =
          Class.forName("org.springframework.web.util.ServletRequestPathUtils");
      Class<?> requestPathClass = Class.forName("org.springframework.http.server.RequestPath");
      return MethodHandles.lookup()
          .findStatic(
              pathUtilsClass,
              "parseAndCache",
              MethodType.methodType(requestPathClass, HttpServletRequest.class));
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
      return null;
    }
  }

  private static void parseAndCache(HttpServletRequest request) {
    if (parseAndCacheMh == null) {
      return;
    }
    try {
      parseAndCacheMh.invoke(request);
    } catch (Throwable throwable) {
      throw new IllegalStateException(throwable);
    }
  }

  private static String getRequestPathAttribute() {
    try {
      Class<?> pathUtilsClass =
          Class.forName("org.springframework.web.util.ServletRequestPathUtils");
      return (String)
          MethodHandles.lookup()
              .findStaticGetter(pathUtilsClass, "PATH_ATTRIBUTE", String.class)
              .invoke();
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException exception) {
      return null;
    } catch (Throwable throwable) {
      throw new IllegalStateException(throwable);
    }
  }

  public class ContentCachingResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream content = new ByteArrayOutputStream(1024);

    private final ServletOutputStream outputStream = new ResponseServletOutputStream();

    private PrintWriter writer;

    private int statusCode = HttpServletResponse.SC_OK;

    /**
     * Create a new ContentCachingResponseWrapper for the given servlet response.
     *
     * @param response the original servlet response
     */
    public ContentCachingResponseWrapper(HttpServletResponse response) {
      super(response);
    }

    @Override
    public void setStatus(int sc) {
      super.setStatus(sc);
      this.statusCode = sc;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setStatus(int sc, String sm) {
      super.setStatus(sc, sm);
      this.statusCode = sc;
    }

    @Override
    public void sendError(int sc) throws IOException {
      copyBodyToResponse();
      super.sendError(sc);
      this.statusCode = sc;
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      copyBodyToResponse();
      super.sendError(sc, msg);
      this.statusCode = sc;
    }

    @Override
    public void sendRedirect(String location) throws IOException {
      copyBodyToResponse();
      super.sendRedirect(location);
    }

    @Override
    public ServletOutputStream getOutputStream() {
      return this.outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
      if (this.writer == null) {
        String characterEncoding = getCharacterEncoding();
        this.writer =
            (characterEncoding != null
                ? new ResponsePrintWriter(characterEncoding)
                : new ResponsePrintWriter(WebUtils.DEFAULT_CHARACTER_ENCODING));
      }
      return this.writer;
    }

    @Override
    public void resetBuffer() {
      this.content.reset();
    }

    @Override
    public void reset() {
      super.reset();
      this.content.reset();
    }

    /** Return the status code as specifed on the response. */
    public int getStatusCode() {
      return this.statusCode;
    }

    /** Return the cached response content as a byte array. */
    public byte[] getContentAsByteArray() {
      return this.content.toByteArray();
    }

    private void copyBodyToResponse() throws IOException {
      if (this.content.size() > 0) {
        getResponse().setContentLength(this.content.size());
        getResponse().getOutputStream().write(this.content.toByteArray());
        this.content.reset();
      }
    }

    private class ResponseServletOutputStream extends ServletOutputStream {

      @Override
      public void write(int b) throws IOException {
        content.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        content.write(b, off, len);
      }

      @Override
      public boolean isReady() {
        return false;
      }

      @Override
      public void setWriteListener(WriteListener writeListener) {}
    }

    private class ResponsePrintWriter extends PrintWriter {

      public ResponsePrintWriter(String characterEncoding) throws UnsupportedEncodingException {
        super(new OutputStreamWriter(content, characterEncoding));
      }

      @Override
      public void write(char[] buf, int off, int len) {
        super.write(buf, off, len);
        super.flush();
      }

      @Override
      public void write(String s, int off, int len) {
        super.write(s, off, len);
        super.flush();
      }

      @Override
      public void write(int c) {
        super.write(c);
        super.flush();
      }
    }
  }
}
