/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR

import grails.artefact.Controller
import grails.web.Action

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
