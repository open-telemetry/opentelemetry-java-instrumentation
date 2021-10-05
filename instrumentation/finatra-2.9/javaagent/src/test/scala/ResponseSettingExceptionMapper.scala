/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder
import javax.inject.{Inject, Singleton}

@Singleton
class ResponseSettingExceptionMapper @Inject() (response: ResponseBuilder)
    extends ExceptionMapper[Exception] {

  override def toResponse(request: Request, exception: Exception): Response = {
    response.internalServerError(exception.getMessage)
  }
}
