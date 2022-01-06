/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import spock.lang.Specification

class IpAddressUtilTest extends Specification {

  def "test ip address"() {
    expect:
    assert IpAddressUtilKt.isIpAddress("2001:0660:7401:0200:0000:0000:0edf:bdd7")
    assert !IpAddressUtilKt.isIpAddress("2001:0660:7401:0200:0000:0000:0edf:bdd7:33")
    assert IpAddressUtilKt.isIpAddress("127.0.0.1")
    assert !IpAddressUtilKt.isIpAddress("127.0.0.1.1")
    assert !IpAddressUtilKt.isIpAddress("localhost")
  }
}
