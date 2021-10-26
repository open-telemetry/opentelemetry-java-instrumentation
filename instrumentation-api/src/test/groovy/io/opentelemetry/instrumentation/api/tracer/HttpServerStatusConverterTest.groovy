/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer

import io.opentelemetry.api.trace.StatusCode
import spock.lang.Specification

class HttpServerStatusConverterTest extends Specification {

  def "test HTTP #httpStatus to OTel #expectedStatus"() {
    when:
    def status = HttpStatusConverter.SERVER.statusFromHttpStatus(httpStatus)

    then:
    status == expectedStatus

    // https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
    where:
    httpStatus | expectedStatus
    100        | StatusCode.UNSET
    101        | StatusCode.UNSET
    102        | StatusCode.UNSET
    103        | StatusCode.UNSET

    200        | StatusCode.UNSET
    201        | StatusCode.UNSET
    202        | StatusCode.UNSET
    203        | StatusCode.UNSET
    204        | StatusCode.UNSET
    205        | StatusCode.UNSET
    206        | StatusCode.UNSET
    207        | StatusCode.UNSET
    208        | StatusCode.UNSET
    226        | StatusCode.UNSET

    300        | StatusCode.UNSET
    301        | StatusCode.UNSET
    302        | StatusCode.UNSET
    303        | StatusCode.UNSET
    304        | StatusCode.UNSET
    305        | StatusCode.UNSET
    306        | StatusCode.UNSET
    307        | StatusCode.UNSET
    308        | StatusCode.UNSET

    400        | StatusCode.UNSET
    401        | StatusCode.UNSET
    403        | StatusCode.UNSET
    404        | StatusCode.UNSET
    405        | StatusCode.UNSET
    406        | StatusCode.UNSET
    407        | StatusCode.UNSET
    408        | StatusCode.UNSET
    409        | StatusCode.UNSET
    410        | StatusCode.UNSET
    411        | StatusCode.UNSET
    412        | StatusCode.UNSET
    413        | StatusCode.UNSET
    414        | StatusCode.UNSET
    415        | StatusCode.UNSET
    416        | StatusCode.UNSET
    417        | StatusCode.UNSET
    418        | StatusCode.UNSET
    421        | StatusCode.UNSET
    422        | StatusCode.UNSET
    423        | StatusCode.UNSET
    424        | StatusCode.UNSET
    425        | StatusCode.UNSET
    426        | StatusCode.UNSET
    428        | StatusCode.UNSET
    429        | StatusCode.UNSET
    431        | StatusCode.UNSET
    451        | StatusCode.UNSET

    500        | StatusCode.ERROR
    501        | StatusCode.ERROR
    502        | StatusCode.ERROR
    503        | StatusCode.ERROR
    504        | StatusCode.ERROR
    505        | StatusCode.ERROR
    506        | StatusCode.ERROR
    507        | StatusCode.ERROR
    508        | StatusCode.ERROR
    510        | StatusCode.ERROR
    511        | StatusCode.ERROR

    // Don't exist
    99         | StatusCode.ERROR
    600        | StatusCode.ERROR
  }
}
