/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5

import io.grpc.Status
import io.opentelemetry.api.trace.StatusCode
import spock.lang.Specification

class GrpcHelperTest extends Specification {

  def "test status from #grpcStatus.code"() {
    when:
    def status = GrpcHelper.statusFromGrpcStatus(grpcStatus)

    then:
    if (grpcStatus == Status.OK) {
      status == StatusCode.UNSET
    } else {
      status == StatusCode.ERROR
    }

    // Considering history of status, if we compare all values of the gRPC status by name, we will
    // probably find any new mismatches with the OpenTelemetry spec.
    where:
    grpcStatus << Status.Code.values().collect { Status.fromCode(it) }
  }

  def "test status has grpc description"() {
    when:
    def status = GrpcHelper.statusFromGrpcStatus(Status.INVALID_ARGUMENT.withDescription("bad argument"))

    then:
    status == StatusCode.ERROR
  }
}
