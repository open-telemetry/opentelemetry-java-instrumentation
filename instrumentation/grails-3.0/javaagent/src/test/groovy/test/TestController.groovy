/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import grails.artefact.Controller
import grails.web.Action
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.testing.GlobalTraceUtil
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import java.util.concurrent.Callable

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

class TestController implements Controller {

  @Action
  def index() {
    render "Hello World!"
  }

  @Action
  def success() {
    controller(SUCCESS) {
      render SUCCESS.body
    }
  }

  @Action
  def query() {
    controller(QUERY_PARAM) {
      render "some=${params.some}"
    }
  }

  @Action
  def redirect() {
    controller(REDIRECT) {
      response.sendRedirect(REDIRECT.body)
    }
  }

  @Action
  def error() {
    controller(ERROR) {
      response.sendError(ERROR.status, "unused")
    }
  }

  @Action
  def exception() {
    controller(EXCEPTION) {
      throw new Exception(EXCEPTION.body)
    }
  }

  @Action
  def path() {
    controller(PATH_PARAM) {
      render params.id
    }
  }

  @Action
  def captureHeaders() {
    controller(CAPTURE_HEADERS) {
      response.setHeader("X-Test-Response", request.getHeader("X-Test-Request"))
      render CAPTURE_HEADERS.body
    }
  }

  @Action
  def child() {
    controller(INDEXED_CHILD) {
      INDEXED_CHILD.collectSpanAttributes({ name -> name == "id" ? params.id : null })
      render INDEXED_CHILD.body
    }
  }

  // copied from HttpServerTest
  // groovy classes in testing-common are compiled with a different groovy version, we don't want
  // to use them here
  static <T> T controller(ServerEndpoint endpoint, Callable<T> closure) {
    assert Span.current().getSpanContext().isValid(): "Controller should have a parent span."
    if (endpoint == NOT_FOUND) {
      return closure.call()
    }
    return GlobalTraceUtil.runWithSpan("controller") {
      closure.call()
    }
  }}
