/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import grails.artefact.Controller
import grails.web.Action
import io.opentelemetry.instrumentation.test.base.HttpServerTest

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
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
    HttpServerTest.controller(SUCCESS) {
      render SUCCESS.body
    }
  }

  @Action
  def query() {
    HttpServerTest.controller(QUERY_PARAM) {
      render "some=${params.some}"
    }
  }

  @Action
  def redirect() {
    HttpServerTest.controller(REDIRECT) {
      response.sendRedirect(REDIRECT.body)
    }
  }

  @Action
  def error() {
    HttpServerTest.controller(ERROR) {
      response.sendError(ERROR.status, "unused")
    }
  }

  @Action
  def exception() {
    HttpServerTest.controller(EXCEPTION) {
      throw new Exception(EXCEPTION.body)
    }
  }

  @Action
  def path() {
    HttpServerTest.controller(PATH_PARAM) {
      render params.id
    }
  }

  @Action
  def captureHeaders() {
    HttpServerTest.controller(CAPTURE_HEADERS) {
      response.setHeader("X-Test-Response", request.getHeader("X-Test-Request"))
      render CAPTURE_HEADERS.body
    }
  }

  @Action
  def child() {
    HttpServerTest.controller(INDEXED_CHILD) {
      INDEXED_CHILD.collectSpanAttributes({ name -> name == "id" ? params.id : null })
      render INDEXED_CHILD.body
    }
  }
}
