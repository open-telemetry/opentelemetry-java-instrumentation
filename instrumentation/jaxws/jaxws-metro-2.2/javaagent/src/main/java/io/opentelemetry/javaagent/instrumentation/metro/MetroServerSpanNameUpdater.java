/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import com.sun.xml.ws.api.message.Packet;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

final class MetroServerSpanNameUpdater {

  private static final Logger logger = Logger.getLogger(MetroServerSpanNameUpdater.class.getName());

  /**
   * Map of message context key names to the {@link HttpServletRequestAdapter} to handle the {@code
   * HttpServletRequest} found at that message context key.
   *
   * <p>This map will contain at most two entries:
   *
   * <ul>
   *   <li>{@value javax.xml.ws.handler.MessageContext#SERVLET_REQUEST} to an {@link
   *       HttpServletRequestAdapter} that handles {@code javax.servlet.http.HttpServletRequest}
   *   <li>{@value jakarta.xml.ws.handler.MessageContext#SERVLET_REQUEST} to an {@link
   *       HttpServletRequestAdapter} that handles {@code jakarta.servlet.http.HttpServletRequest}
   * </ul>
   */
  private final Map<String, HttpServletRequestAdapter> servletRequestAdapters;

  public MetroServerSpanNameUpdater() {
    this.servletRequestAdapters = new LinkedHashMap<>();

    registerHttpServletRequestAdapter(
        "Jakarta EE",
        // Same as jakarta.xml.ws.handler.MessageContext.SERVLET_REQUEST
        "jakarta.xml.ws.servlet.request",
        "jakarta.servlet.http.HttpServletRequest");

    registerHttpServletRequestAdapter(
        "Java EE",
        // Same as javax.xml.ws.handler.MessageContext.SERVLET_REQUEST
        "javax.xml.ws.servlet.request",
        "javax.servlet.http.HttpServletRequest");
  }

  /**
   * Registers a {@link HttpServletRequestAdapter} in the {@link #servletRequestAdapters} with the
   * given {@code key} if the given {@code httpServletRequestClassName} is on the classpath.
   */
  private void registerHttpServletRequestAdapter(
      String name, String key, String httpServletRequestClassName) {
    HttpServletRequestAdapter adapter;
    try {
      adapter = new HttpServletRequestAdapter(Class.forName(httpServletRequestClassName));
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
      // Ignore.  Don't register
      return;
    }
    servletRequestAdapters.put(key, adapter);
    logger.finest(() -> "Enabled " + name + " jaxws metro server span naming");
  }

  public void updateServerSpanName(Context context, MetroRequest metroRequest) {
    String spanName = metroRequest.spanName();
    if (spanName == null) {
      return;
    }

    Span serverSpan = LocalRootSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    for (Map.Entry<String, HttpServletRequestAdapter> httpServletRequestAdapterEntry :
        servletRequestAdapters.entrySet()) {
      Packet packet = metroRequest.packet();
      String key = httpServletRequestAdapterEntry.getKey();
      if (packet.supports(key)) {
        Object request = packet.get(key);
        HttpServletRequestAdapter httpServletRequestAdapter =
            httpServletRequestAdapterEntry.getValue();
        if (httpServletRequestAdapter.canHandle(request)) {
          String servletPath = httpServletRequestAdapter.getServletPath(request);
          if (servletPath != null && !servletPath.isEmpty()) {
            String pathInfo = httpServletRequestAdapter.getPathInfo(request);
            if (pathInfo != null) {
              spanName = servletPath + "/" + spanName;
            } else {
              // when pathInfo is null then there is a servlet that is mapped to this exact service
              // servletPath already contains the service name
              String operationName = packet.getWSDLOperation().getLocalPart();
              spanName = servletPath + "/" + operationName;
            }
            break;
          }
        }
      }
    }

    serverSpan.updateName(ServletContextPath.prepend(context, spanName));
  }

  /**
   * Adapter class for accessing the methods needed from either {@code
   * jakarta.servlet.http.HttpServletRequest} or {@code javax.servlet.http.HttpServletRequest}.
   */
  private static class HttpServletRequestAdapter {

    private final Class<?> httpServletRequestClass;
    private final MethodHandle getServletPathMethodHandle;
    private final MethodHandle getPathInfoMethodHandle;

    private HttpServletRequestAdapter(Class<?> httpServletRequestClass)
        throws NoSuchMethodException, IllegalAccessException {
      this.httpServletRequestClass =
          Objects.requireNonNull(
              httpServletRequestClass, "httpServletRequestClass must not be null");

      MethodHandles.Lookup lookup = MethodHandles.lookup();
      this.getServletPathMethodHandle =
          lookup.unreflect(httpServletRequestClass.getMethod("getServletPath"));
      this.getPathInfoMethodHandle =
          lookup.unreflect(httpServletRequestClass.getMethod("getPathInfo"));
    }

    public boolean canHandle(Object httpServletRequest) {
      return httpServletRequestClass.isInstance(httpServletRequest);
    }

    public String getServletPath(Object httpServletRequest) {
      return invokeSafely(getServletPathMethodHandle, httpServletRequest);
    }

    public String getPathInfo(Object httpServletRequest) {
      return invokeSafely(getPathInfoMethodHandle, httpServletRequest);
    }

    private static String invokeSafely(MethodHandle methodHandle, Object httpServletRequest) {
      try {
        return (String) methodHandle.invoke(httpServletRequest);
      } catch (UnsupportedOperationException e) {
        // when request wrapper throws UnsupportedOperationException return null to skip adding
        // servlet path to the span name
        // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10986
        return null;
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Throwable t) {
        /*
         * This is impossible, because the methods being invoked do not throw checked exceptions,
         * and unchecked exceptions and errors are handled above
         */
        throw new AssertionError(t);
      }
    }
  }
}
