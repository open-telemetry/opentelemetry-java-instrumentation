/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import grails.artefact.Controller
import grails.web.Action

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR

class ErrorController implements Controller {

  @Action
  def index() {
    render ERROR.body
  }

  @Action
  def notFound() {
    response.sendError(404, "Not Found")
  }
}
