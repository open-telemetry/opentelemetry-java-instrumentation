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

package io.opentelemetry.instrumentation.auto.grpc.common

import io.grpc.Status
import spock.lang.Specification

class GrpcHelperTest extends Specification {

  def "test status from #grpcStatus.code"() {
    when:
    def status = GrpcHelper.statusFromGrpcStatus(grpcStatus)

    then:
    if (grpcStatus == Status.OK) {
      status.canonicalCode == io.opentelemetry.trace.Status.CanonicalCode.UNSET
    } else {
      status.canonicalCode == io.opentelemetry.trace.Status.CanonicalCode.ERROR
    }
    status.description == null

    // Considering history of status, if we compare all values of the gRPC status by name, we will
    // probably find any new mismatches with the OpenTelemetry spec.
    where:
    grpcStatus << Status.Code.values().collect { Status.fromCode(it) }
  }

  def "test status has grpc description"() {
    when:
    def status = GrpcHelper.statusFromGrpcStatus(Status.INVALID_ARGUMENT.withDescription("bad argument"))

    then:
    status.canonicalCode == io.opentelemetry.trace.Status.CanonicalCode.ERROR
    status.description == "bad argument"
  }
}
