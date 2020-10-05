/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.decorator

import io.opentelemetry.trace.Status
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
    100        | Status.UNSET
    101        | Status.UNSET
    102        | Status.UNSET
    103        | Status.UNSET

    200        | Status.UNSET
    201        | Status.UNSET
    202        | Status.UNSET
    203        | Status.UNSET
    204        | Status.UNSET
    205        | Status.UNSET
    206        | Status.UNSET
    207        | Status.UNSET
    208        | Status.UNSET
    226        | Status.UNSET

    300        | Status.UNSET
    301        | Status.UNSET
    302        | Status.UNSET
    303        | Status.UNSET
    304        | Status.UNSET
    305        | Status.UNSET
    306        | Status.UNSET
    307        | Status.UNSET
    308        | Status.UNSET

    400        | Status.ERROR
    401        | Status.ERROR
    403        | Status.ERROR
    404        | Status.ERROR
    405        | Status.ERROR
    406        | Status.ERROR
    407        | Status.ERROR
    408        | Status.ERROR
    409        | Status.ERROR
    410        | Status.ERROR
    411        | Status.ERROR
    412        | Status.ERROR
    413        | Status.ERROR
    414        | Status.ERROR
    415        | Status.ERROR
    416        | Status.ERROR
    417        | Status.ERROR
    418        | Status.ERROR
    421        | Status.ERROR
    422        | Status.ERROR
    423        | Status.ERROR
    424        | Status.ERROR
    425        | Status.ERROR
    426        | Status.ERROR
    428        | Status.ERROR
    429        | Status.ERROR
    431        | Status.ERROR
    451        | Status.ERROR

    500        | Status.ERROR
    501        | Status.ERROR
    502        | Status.ERROR
    503        | Status.ERROR
    504        | Status.ERROR
    505        | Status.ERROR
    506        | Status.ERROR
    507        | Status.ERROR
    508        | Status.ERROR
    510        | Status.ERROR
    511        | Status.ERROR

    // Don't exist
    99         | Status.ERROR
    600        | Status.ERROR
  }
}
