/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0

import org.restlet.data.Form
import org.restlet.data.Reference
import org.restlet.data.Status
import org.restlet.resource.Get
import org.restlet.resource.ServerResource

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.controller

class RestletAppTestBase {

  static class SuccessResource extends ServerResource {

    @Get("txt")
    String represent() {
      controller(SUCCESS) {
        return SUCCESS.getBody()
      }
    }

  }

  static class ErrorResource extends ServerResource {

    @Get("txt")
    String represent() {
      controller(ERROR) {
        getResponse().setStatus(Status.valueOf(ERROR.getStatus()), ERROR.getBody())
        return ERROR.getBody()
      }
    }

  }

  static class ExceptionResource extends ServerResource {

    @Get("txt")
    String represent() {
      controller(EXCEPTION) {
        throw new Exception(EXCEPTION.getBody())
      }
    }

  }

  static class QueryParamResource extends ServerResource {

    @Get("txt")
    String represent() {
      controller(QUERY_PARAM) {
        return QUERY_PARAM.getBody()
      }
    }

  }

  static class PathParamResource extends ServerResource {

    @Get("txt")
    String represent() {
      controller(PATH_PARAM) {
        return PATH_PARAM.getBody()
      }
    }

  }

  static class RedirectResource extends ServerResource {

    @Get("txt")
    String represent() {
      controller(REDIRECT) {
        redirectSeeOther(new Reference(getRootRef().toString() + REDIRECT.getBody()))
        response.setStatus(Status.valueOf(REDIRECT.getStatus()))
        return ""
      }
    }

  }

  static class CaptureHeadersResource extends ServerResource {

    @Get("txt")
    String represent() {
      controller(CAPTURE_HEADERS) {
        def requestHeaders = request.getAttributes().get("org.restlet.http.headers")
        def responseHeaders

        try {
          def headerClass = Class.forName("org.restlet.data.Header")
          def seriesClass = Class.forName("org.restlet.util.Series")
          //to avoid constructor error (Series is abstract in 2.0.x)
          responseHeaders = response.getAttributes().computeIfAbsent("org.restlet.http.headers", { seriesClass.newInstance(headerClass) })

        } catch (ClassNotFoundException | NoClassDefFoundError e) {

          responseHeaders = response.getAttributes().computeIfAbsent("org.restlet.http.headers", { new Form() })
        }

        responseHeaders.add("X-Test-Response", requestHeaders.getValues("X-Test-Request"))
        return CAPTURE_HEADERS.getBody()
      }
    }

  }


  static class IndexedChildResource extends ServerResource {

    @Get("txt")
    String represent() {
      controller(INDEXED_CHILD) {
        INDEXED_CHILD.collectSpanAttributes {
          request.getOriginalRef().getQueryAsForm().getFirst(it).getValue()
        }
        //INDEXED_CHILD.getBody() returns an empty string, in which case Restlet sets status to 204
        return "child"
      }
    }

  }

}
