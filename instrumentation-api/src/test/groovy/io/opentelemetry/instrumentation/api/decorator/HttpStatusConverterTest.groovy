/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.decorator

import io.opentelemetry.trace.StatusCanonicalCode
import spock.lang.Specification

class HttpStatusConverterTest extends Specification {

  def "test HTTP #httpStatus to OTel #expectedStatus"() {
    when:
    def status = HttpStatusConverter.statusFromHttpStatus(httpStatus)

    then:
    status == expectedStatus

    // https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
    where:
    httpStatus | expectedStatus
    100        | StatusCanonicalCode.UNSET
    101        | StatusCanonicalCode.UNSET
    102        | StatusCanonicalCode.UNSET
    103        | StatusCanonicalCode.UNSET

    200        | StatusCanonicalCode.UNSET
    201        | StatusCanonicalCode.UNSET
    202        | StatusCanonicalCode.UNSET
    203        | StatusCanonicalCode.UNSET
    204        | StatusCanonicalCode.UNSET
    205        | StatusCanonicalCode.UNSET
    206        | StatusCanonicalCode.UNSET
    207        | StatusCanonicalCode.UNSET
    208        | StatusCanonicalCode.UNSET
    226        | StatusCanonicalCode.UNSET

    300        | StatusCanonicalCode.UNSET
    301        | StatusCanonicalCode.UNSET
    302        | StatusCanonicalCode.UNSET
    303        | StatusCanonicalCode.UNSET
    304        | StatusCanonicalCode.UNSET
    305        | StatusCanonicalCode.UNSET
    306        | StatusCanonicalCode.UNSET
    307        | StatusCanonicalCode.UNSET
    308        | StatusCanonicalCode.UNSET

    400        | StatusCanonicalCode.ERROR
    401        | StatusCanonicalCode.ERROR
    403        | StatusCanonicalCode.ERROR
    404        | StatusCanonicalCode.ERROR
    405        | StatusCanonicalCode.ERROR
    406        | StatusCanonicalCode.ERROR
    407        | StatusCanonicalCode.ERROR
    408        | StatusCanonicalCode.ERROR
    409        | StatusCanonicalCode.ERROR
    410        | StatusCanonicalCode.ERROR
    411        | StatusCanonicalCode.ERROR
    412        | StatusCanonicalCode.ERROR
    413        | StatusCanonicalCode.ERROR
    414        | StatusCanonicalCode.ERROR
    415        | StatusCanonicalCode.ERROR
    416        | StatusCanonicalCode.ERROR
    417        | StatusCanonicalCode.ERROR
    418        | StatusCanonicalCode.ERROR
    421        | StatusCanonicalCode.ERROR
    422        | StatusCanonicalCode.ERROR
    423        | StatusCanonicalCode.ERROR
    424        | StatusCanonicalCode.ERROR
    425        | StatusCanonicalCode.ERROR
    426        | StatusCanonicalCode.ERROR
    428        | StatusCanonicalCode.ERROR
    429        | StatusCanonicalCode.ERROR
    431        | StatusCanonicalCode.ERROR
    451        | StatusCanonicalCode.ERROR

    500        | StatusCanonicalCode.ERROR
    501        | StatusCanonicalCode.ERROR
    502        | StatusCanonicalCode.ERROR
    503        | StatusCanonicalCode.ERROR
    504        | StatusCanonicalCode.ERROR
    505        | StatusCanonicalCode.ERROR
    506        | StatusCanonicalCode.ERROR
    507        | StatusCanonicalCode.ERROR
    508        | StatusCanonicalCode.ERROR
    510        | StatusCanonicalCode.ERROR
    511        | StatusCanonicalCode.ERROR

    // Don't exist
    99         | StatusCanonicalCode.ERROR
    600        | StatusCanonicalCode.ERROR
  }
}
