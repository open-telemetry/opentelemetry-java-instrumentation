/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import org.restlet.data.Form;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

class RestletAppTestBase {

  public static class SuccessResource extends ServerResource {

    @Get("txt")
    public String represent() {
      return controller(SUCCESS, SUCCESS::getBody);
    }
  }

  public static class ErrorResource extends ServerResource {

    @Get("txt")
    public String represent() {
      return controller(
          ERROR,
          () -> {
            getResponse().setStatus(Status.valueOf(ERROR.getStatus()), ERROR.getBody());
            return ERROR.getBody();
          });
    }
  }

  public static class ExceptionResource extends ServerResource {

    @Get("txt")
    public String represent() {
      return controller(
          EXCEPTION,
          () -> {
            throw new IllegalStateException(EXCEPTION.getBody());
          });
    }
  }

  public static class QueryParamResource extends ServerResource {

    @Get("txt")
    public String represent() {
      return controller(QUERY_PARAM, QUERY_PARAM::getBody);
    }
  }

  public static class PathParamResource extends ServerResource {

    @Get("txt")
    public String represent() {
      return controller(PATH_PARAM, PATH_PARAM::getBody);
    }
  }

  public static class RedirectResource extends ServerResource {

    @Get("txt")
    public String represent() {
      return controller(
          REDIRECT,
          () -> {
            redirectSeeOther(new Reference(getRootRef().toString() + REDIRECT.getBody()));
            getResponse().setStatus(Status.valueOf(REDIRECT.getStatus()));
            return "";
          });
    }
  }

  @SuppressWarnings("unchecked")
  static void handleCaptureHeaders(Object request, Object response) {
    Map<String, Object> attributes;
    Map<String, Object> responseAttributes;
    try {
      Method requestAttributesMethod = request.getClass().getMethod("getAttributes");
      attributes = (Map<String, Object>) requestAttributesMethod.invoke(request);

      Method responseAttributesMethod = response.getClass().getMethod("getAttributes");
      responseAttributes = (Map<String, Object>) responseAttributesMethod.invoke(response);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
      throw new IllegalStateException(exception);
    }
    Series<?> requestHeaders = (Series<?>) attributes.get("org.restlet.http.headers");
    Series<?> responseHeaders;

    try {
      Class<?> headerClass = Class.forName("org.restlet.data.Header");
      Class<?> seriesClass = Class.forName("org.restlet.util.Series");
      Constructor<?> constructor = seriesClass.getConstructor(Class.class);
      // to avoid constructor error (Series is abstract in 2.0.x)
      responseHeaders =
          (Series<?>)
              responseAttributes.computeIfAbsent(
                  "org.restlet.http.headers",
                  (key) -> {
                    try {
                      return constructor.newInstance(headerClass);
                    } catch (Exception exception) {
                      throw new IllegalStateException(exception);
                    }
                  });
    } catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException exception) {
      responseHeaders =
          (Series<?>)
              responseAttributes.computeIfAbsent("org.restlet.http.headers", (key) -> new Form());
    }

    responseHeaders.add("X-Test-Response", requestHeaders.getValues("X-Test-Request"));
  }

  public static class CaptureHeadersResource extends ServerResource {

    @Get("txt")
    public String represent() {
      return controller(
          CAPTURE_HEADERS,
          () -> {
            handleCaptureHeaders(getRequest(), getResponse());
            return CAPTURE_HEADERS.getBody();
          });
    }
  }

  public static class IndexedChildResource extends ServerResource {

    @Get("txt")
    public String represent() {
      return controller(
          INDEXED_CHILD,
          () -> {
            INDEXED_CHILD.collectSpanAttributes(
                name -> getRequest().getOriginalRef().getQueryAsForm().getFirstValue(name));
            // INDEXED_CHILD.getBody() returns an empty string, in which case Restlet sets status to
            // 204
            return "child";
          });
    }
  }

  private RestletAppTestBase() {}
}
