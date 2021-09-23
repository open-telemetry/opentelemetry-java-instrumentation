/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import grails.artefact.Controller
import grails.web.Action
import io.opentelemetry.instrumentation.test.base.HttpServerTest

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

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
}
