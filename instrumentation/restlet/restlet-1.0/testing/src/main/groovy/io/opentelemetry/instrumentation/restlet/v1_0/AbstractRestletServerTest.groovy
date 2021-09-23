/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.restlet.Component
import org.restlet.Context
import org.restlet.Redirector
import org.restlet.Restlet
import org.restlet.Server
import org.restlet.VirtualHost
import org.restlet.data.MediaType
import org.restlet.data.Protocol
import org.restlet.data.Request
import org.restlet.data.Response
import org.restlet.data.Status

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

abstract class AbstractRestletServerTest extends HttpServerTest<Server> {

  Component component
  VirtualHost host

  @Override
  Server startServer(int port) {

    component = new Component()
    def server = component.getServers().add(Protocol.HTTP, port)

    host = component.getDefaultHost()
    attachRestlets()
    component.start()

    return server
  }

  @Override
  void stopServer(Server server) {
    component.stop()
  }

  def attachAndWrap(path, restlet){
    host.attach(path, wrapRestlet(restlet, path))
  }

  def attachRestlets(){
    attachAndWrap(SUCCESS.path, new Restlet() {
      @Override
      void handle(Request request, Response response) {
        controller(SUCCESS) {
          response.setEntity(SUCCESS.body, MediaType.TEXT_PLAIN)
          response.setStatus(Status.valueOf(SUCCESS.status), SUCCESS.body)
        }
      }
    })

    attachAndWrap(REDIRECT.path, new Redirector(Context.getCurrent(), REDIRECT.body, Redirector.MODE_CLIENT_FOUND) {
      @Override
      void handle(Request request, Response response){
        super.handle(request, response)
        controller(REDIRECT){
        } //TODO: check why handle fails inside controller
      }
    })

    attachAndWrap(ERROR.path, new Restlet(){
      @Override
      void handle(Request request, Response response){
        controller(ERROR){
          response.setStatus(Status.valueOf(ERROR.getStatus()), ERROR.getBody())
        }
      }
    })

    attachAndWrap(EXCEPTION.path, new Restlet(){
      @Override
      void handle(Request request, Response response){
        controller(EXCEPTION){
          throw new Exception(EXCEPTION.getBody())
        }
      }
    })

    attachAndWrap(QUERY_PARAM.path, new Restlet() {
      @Override
      void handle(Request request, Response response){
        controller(QUERY_PARAM){
          response.setEntity(QUERY_PARAM.getBody(), MediaType.TEXT_PLAIN)
          response.setStatus(Status.valueOf(QUERY_PARAM.getStatus()), QUERY_PARAM.getBody())
        }
      }
    })

    attachAndWrap(NOT_FOUND.path, new Restlet() {
      @Override
      void handle(Request request, Response response){
        controller(NOT_FOUND){
          response.setEntity(NOT_FOUND.getBody(), MediaType.TEXT_PLAIN)
          response.setStatus(Status.valueOf(NOT_FOUND.getStatus()), NOT_FOUND.getBody())
        }
      }
    })

    attachAndWrap("/path/{id}/param", new Restlet(){
      @Override
      void handle(Request request, Response response) {
        controller(PATH_PARAM) {
          response.setEntity(PATH_PARAM.getBody(), MediaType.TEXT_PLAIN)
          response.setStatus(Status.valueOf(PATH_PARAM.getStatus()), PATH_PARAM.getBody())
        }
      }
    })

    attachAndWrap(INDEXED_CHILD.path, new Restlet() {
      @Override
      void handle(Request request, Response response) {
        controller(INDEXED_CHILD) {
          INDEXED_CHILD.collectSpanAttributes {request.getOriginalRef().getQueryAsForm().getFirst(it).getValue() }
          response.setStatus(Status.valueOf(INDEXED_CHILD.status))
        }
      }
    })

  }

  @Override
  List<AttributeKey<?>> extraAttributes() {
    [
      SemanticAttributes.HTTP_TARGET,
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.NET_TRANSPORT,
      ]
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  boolean testConcurrency() {
    true
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    switch (endpoint) {
      case PATH_PARAM:
        return getContextPath() + "/path/{id}/param"
      case NOT_FOUND:
        return getContextPath() + "/notFound"
      default:
        return endpoint.resolvePath(address).path
    }
  }

  abstract Restlet wrapRestlet(Restlet restlet, String path)

}
