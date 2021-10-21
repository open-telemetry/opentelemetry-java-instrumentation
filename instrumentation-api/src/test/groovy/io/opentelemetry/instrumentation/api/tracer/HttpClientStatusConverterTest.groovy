/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer

import io.opentelemetry.api.trace.StatusCode
import spock.lang.Specification

class HttpClientStatusConverterTest extends Specification {

  def "test HTTP #httpStatus to OTel #expectedStatus"() {
    when:
    def status = HttpStatusConverter.CLIENT.statusFromHttpStatus(httpStatus)

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

    400        | StatusCode.ERROR
    401        | StatusCode.ERROR
    403        | StatusCode.ERROR
    404        | StatusCode.ERROR
    405        | StatusCode.ERROR
    406        | StatusCode.ERROR
    407        | StatusCode.ERROR
    408        | StatusCode.ERROR
    409        | StatusCode.ERROR
    410        | StatusCode.ERROR
    411        | StatusCode.ERROR
    412        | StatusCode.ERROR
    413        | StatusCode.ERROR
    414        | StatusCode.ERROR
    415        | StatusCode.ERROR
    416        | StatusCode.ERROR
    417        | StatusCode.ERROR
    418        | StatusCode.ERROR
    421        | StatusCode.ERROR
    422        | StatusCode.ERROR
    423        | StatusCode.ERROR
    424        | StatusCode.ERROR
    425        | StatusCode.ERROR
    426        | StatusCode.ERROR
    428        | StatusCode.ERROR
    429        | StatusCode.ERROR
    431        | StatusCode.ERROR
    451        | StatusCode.ERROR

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
