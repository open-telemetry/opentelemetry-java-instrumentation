/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
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
