/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import play.api.GlobalSettings
import play.api.mvc.{RequestHeader, Result, Results}

import scala.concurrent.Future

class Settings extends GlobalSettings {
  override def onError(
      request: RequestHeader,
      ex: Throwable
  ): Future[Result] = {
    Future.successful(Results.InternalServerError(ex.getCause.getMessage))
  }
}
