/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0.spring

import io.opentelemetry.instrumentation.restlet.v1_0.AbstractRestletServerTest
import org.restlet.Component
import org.restlet.Context
import org.restlet.Router
import org.restlet.Server
import org.restlet.data.Form
import org.restlet.data.MediaType
import org.restlet.data.Reference
import org.restlet.data.Request
import org.restlet.data.Response
import org.restlet.data.Status
import org.restlet.resource.Representation
import org.restlet.resource.Resource
import org.restlet.resource.StringRepresentation
import org.restlet.resource.Variant
import org.springframework.context.support.ClassPathXmlApplicationContext

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

abstract class AbstractSpringServerTest extends AbstractRestletServerTest {

  Router router

  abstract String getConfigurationName()

  @Override
  Server setupServer(Component component) {
    def context = new ClassPathXmlApplicationContext(getConfigurationName())
    router = (Router) context.getBean("testRouter")
    def server = (Server) context.getBean("testServer", "http", port)
    component.getServers().add(server)
    return server
  }

  @Override
  void setupRouting() {
    host.attach(router)
  }

  static class SuccessResource extends Resource {

    @Override
    void init(Context context, Request request, Response response) {
      super.init(context, request, response)
      getVariants().add(new Variant(MediaType.TEXT_PLAIN))
    }

    @Override
    Representation represent(Variant variant) {
      controller(SUCCESS) {
        return new StringRepresentation(SUCCESS.body)
      }
    }
  }

  static class ErrorResource extends Resource {

    @Override
    void init(Context context, Request request, Response response) {
      super.init(context, request, response)
      getVariants().add(new Variant(MediaType.TEXT_PLAIN))
    }

    @Override
    Representation represent(Variant variant) {
      controller(ERROR) {
        getResponse().setStatus(Status.valueOf(ERROR.getStatus()), ERROR.getBody())
        return new StringRepresentation(ERROR.body)
      }
    }
  }

  static class ExceptionResource extends Resource {

    @Override
    void init(Context context, Request request, Response response) {
      super.init(context, request, response)
      getVariants().add(new Variant(MediaType.TEXT_PLAIN))
    }

    @Override
    Representation represent(Variant variant) {
      controller(ERROR) {
        throw new Exception(EXCEPTION.body)
      }
    }
  }

  static class QueryParamResource extends Resource {

    @Override
    void init(Context context, Request request, Response response) {
      super.init(context, request, response)
      getVariants().add(new Variant(MediaType.TEXT_PLAIN))
    }

    @Override
    Representation represent(Variant variant) {
      controller(QUERY_PARAM) {
        return new StringRepresentation(QUERY_PARAM.getBody())
      }
    }
  }

  static class PathParamResource extends Resource {

    @Override
    void init(Context context, Request request, Response response) {
      super.init(context, request, response)
      getVariants().add(new Variant(MediaType.TEXT_PLAIN))
    }

    @Override
    Representation represent(Variant variant) {
      controller(PATH_PARAM) {
        return new StringRepresentation(PATH_PARAM.getBody())
      }
    }
  }

  static class RedirectResource extends Resource {

    @Override
    void init(Context context, Request request, Response response) {
      super.init(context, request, response)
      getVariants().add(new Variant(MediaType.TEXT_PLAIN))
    }

    @Override
    Representation represent(Variant variant) {
      controller(REDIRECT) {
        response.setLocationRef(new Reference(REDIRECT.getBody()))
        response.setStatus(Status.REDIRECTION_FOUND)
        return new StringRepresentation(REDIRECT.getBody())
      }
    }
  }

  static class CaptureHeadersResource extends Resource {
    @Override
    void init(Context context, Request request, Response response) {
      super.init(context, request, response)
      getVariants().add(new Variant(MediaType.TEXT_PLAIN))
    }

    @Override
    Representation represent(Variant variant) {
      controller(CAPTURE_HEADERS) {
        Form requestHeaders = request.getAttributes().get("org.restlet.http.headers")
        Form responseHeaders = response.getAttributes().computeIfAbsent("org.restlet.http.headers", { new Form() })
        responseHeaders.add("X-Test-Response", requestHeaders.getValues("X-Test-Request"))

        return new StringRepresentation(CAPTURE_HEADERS.getBody())
      }
    }
  }

  static class IndexedChildResource extends Resource {

    @Override
    void init(Context context, Request request, Response response) {
      super.init(context, request, response)
      getVariants().add(new Variant(MediaType.TEXT_PLAIN))
    }

    @Override
    Representation represent(Variant variant) {
      controller(INDEXED_CHILD) {
        INDEXED_CHILD.collectSpanAttributes { request.getOriginalRef().getQueryAsForm().getFirst(it).getValue() }
        return new StringRepresentation(INDEXED_CHILD.getBody())
      }
    }
  }


}
