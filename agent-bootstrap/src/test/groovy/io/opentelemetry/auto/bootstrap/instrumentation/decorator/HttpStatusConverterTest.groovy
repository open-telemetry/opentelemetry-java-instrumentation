/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.bootstrap.instrumentation.decorator

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
    100        | Status.OK
    101        | Status.OK
    102        | Status.OK
    103        | Status.OK

    200        | Status.OK
    201        | Status.OK
    202        | Status.OK
    203        | Status.OK
    204        | Status.OK
    205        | Status.OK
    206        | Status.OK
    207        | Status.OK
    208        | Status.OK
    226        | Status.OK

    300        | Status.OK
    301        | Status.OK
    302        | Status.OK
    303        | Status.OK
    304        | Status.OK
    305        | Status.OK
    306        | Status.OK
    307        | Status.OK
    308        | Status.OK

    400        | Status.INVALID_ARGUMENT
    401        | Status.UNAUTHENTICATED
    403        | Status.PERMISSION_DENIED
    404        | Status.NOT_FOUND
    405        | Status.INVALID_ARGUMENT
    406        | Status.INVALID_ARGUMENT
    407        | Status.INVALID_ARGUMENT
    408        | Status.INVALID_ARGUMENT
    409        | Status.INVALID_ARGUMENT
    410        | Status.INVALID_ARGUMENT
    411        | Status.INVALID_ARGUMENT
    412        | Status.INVALID_ARGUMENT
    413        | Status.INVALID_ARGUMENT
    414        | Status.INVALID_ARGUMENT
    415        | Status.INVALID_ARGUMENT
    416        | Status.INVALID_ARGUMENT
    417        | Status.INVALID_ARGUMENT
    418        | Status.INVALID_ARGUMENT
    421        | Status.INVALID_ARGUMENT
    422        | Status.INVALID_ARGUMENT
    423        | Status.INVALID_ARGUMENT
    424        | Status.INVALID_ARGUMENT
    425        | Status.INVALID_ARGUMENT
    426        | Status.INVALID_ARGUMENT
    428        | Status.INVALID_ARGUMENT
    429        | Status.RESOURCE_EXHAUSTED
    431        | Status.INVALID_ARGUMENT
    451        | Status.INVALID_ARGUMENT

    500        | Status.INTERNAL
    501        | Status.UNIMPLEMENTED
    502        | Status.INTERNAL
    503        | Status.UNAVAILABLE
    504        | Status.DEADLINE_EXCEEDED
    505        | Status.INTERNAL
    506        | Status.INTERNAL
    507        | Status.INTERNAL
    508        | Status.INTERNAL
    510        | Status.INTERNAL
    511        | Status.INTERNAL

    // Don't exist
    99         | Status.UNKNOWN
    600        | Status.UNKNOWN
  }
}
