/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

class RestletAppTestBase {

  abstract static class BaseResource extends Resource {

    @Override
    public void init(Context context, Request request, Response response) {
      super.init(context, request, response);
      getVariants().add(new Variant(MediaType.TEXT_PLAIN));
    }
  }

  public static class SuccessResource extends BaseResource {

    @Override
    public Representation represent(Variant variant) {
      return controller(SUCCESS, () -> new StringRepresentation(SUCCESS.getBody()));
    }
  }

  public static class ErrorResource extends BaseResource {

    @Override
    public Representation represent(Variant variant) {
      return controller(
          ERROR,
          () -> {
            getResponse().setStatus(Status.valueOf(ERROR.getStatus()), ERROR.getBody());
            return new StringRepresentation(ERROR.getBody());
          });
    }
  }

  public static class ExceptionResource extends BaseResource {

    @Override
    public Representation represent(Variant variant) {
      return controller(
          EXCEPTION,
          () -> {
            throw new IllegalStateException(EXCEPTION.getBody());
          });
    }
  }

  public static class QueryParamResource extends BaseResource {

    @Override
    public Representation represent(Variant variant) {
      return controller(QUERY_PARAM, () -> new StringRepresentation(QUERY_PARAM.getBody()));
    }
  }

  public static class PathParamResource extends BaseResource {

    @Override
    public Representation represent(Variant variant) {
      return controller(PATH_PARAM, () -> new StringRepresentation(PATH_PARAM.getBody()));
    }
  }

  public static class RedirectResource extends BaseResource {

    @Override
    public Representation represent(Variant variant) {
      return controller(
          REDIRECT,
          () -> {
            getResponse().setLocationRef(new Reference(REDIRECT.getBody()));
            getResponse().setStatus(Status.valueOf(REDIRECT.getStatus()));
            return new StringRepresentation(REDIRECT.getBody());
          });
    }
  }

  public static class CaptureHeadersResource extends BaseResource {

    @Override
    public Representation represent(Variant variant) {
      return controller(
          CAPTURE_HEADERS,
          () -> {
            Form requestHeaders =
                (Form) getRequest().getAttributes().get("org.restlet.http.headers");
            Form responseHeaders =
                (Form)
                    getResponse()
                        .getAttributes()
                        .computeIfAbsent("org.restlet.http.headers", (key) -> new Form());
            responseHeaders.add("X-Test-Response", requestHeaders.getValues("X-Test-Request"));
            return new StringRepresentation(CAPTURE_HEADERS.getBody());
          });
    }
  }

  public static class IndexedChildResource extends BaseResource {

    @Override
    public Representation represent(Variant variant) {
      return controller(
          INDEXED_CHILD,
          () -> {
            INDEXED_CHILD.collectSpanAttributes(
                name -> getRequest().getOriginalRef().getQueryAsForm().getFirst(name).getValue());
            return new StringRepresentation(INDEXED_CHILD.getBody());
          });
    }
  }

  private RestletAppTestBase() {}
}
