/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import grails.artefact.Controller
import grails.web.Action

class ErrorController implements Controller {

  @Action
  def index() {
    render "Error"
  }

  @Action
  def notFound() {
    response.sendError(404, "Not Found")
  }
}
