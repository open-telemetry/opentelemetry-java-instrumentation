/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker
import io.opentelemetry.javaagent.instrumentation.internal.proxy.ProxyHelper
import spock.lang.Specification

import java.util.concurrent.Callable

class ProxyHelperTest extends Specification {

  def "should filter #interfaces"() {
    expect:
    ProxyHelper.filtered(interfaces.toArray() as Class<?>[]) == filtered.toArray()

    where:
    interfaces                                                                             | filtered
    []                                                                                     | []
    [Runnable]                                                                             | [Runnable]
    [Runnable, Callable]                                                                   | [Runnable, Callable]
    [Runnable, VirtualFieldInstalledMarker]                              | [Runnable, VirtualFieldInstalledMarker]
    [Runnable, VirtualFieldInstalledMarker, VirtualFieldInstalledMarker] | [Runnable, VirtualFieldInstalledMarker]
    [VirtualFieldInstalledMarker, Runnable]                              | [VirtualFieldInstalledMarker, Runnable]
    [VirtualFieldInstalledMarker, Runnable, VirtualFieldInstalledMarker] | [VirtualFieldInstalledMarker, Runnable]
    [VirtualFieldInstalledMarker, VirtualFieldInstalledMarker, Runnable] | [VirtualFieldInstalledMarker, Runnable]
  }
}
