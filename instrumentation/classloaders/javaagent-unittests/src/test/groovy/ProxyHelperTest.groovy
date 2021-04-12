/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.javaagent.bootstrap.FieldBackedContextStoreAppliedMarker
import io.opentelemetry.javaagent.instrumentation.javaclassloader.ProxyHelper
import java.util.concurrent.Callable
import spock.lang.Specification

class ProxyHelperTest extends Specification {

  def "should filter #interfaces"() {
    expect:
    ProxyHelper.filtered(interfaces.toArray() as Class<?>[]) == filtered.toArray()

    where:
    interfaces                                                                             | filtered
    []                                                                                     | []
    [Runnable]                                                                             | [Runnable]
    [Runnable, Callable]                                                                   | [Runnable, Callable]
    [Runnable, FieldBackedContextStoreAppliedMarker]                                       | [Runnable, FieldBackedContextStoreAppliedMarker]
    [Runnable, FieldBackedContextStoreAppliedMarker, FieldBackedContextStoreAppliedMarker] | [Runnable, FieldBackedContextStoreAppliedMarker]
    [FieldBackedContextStoreAppliedMarker, Runnable]                                       | [FieldBackedContextStoreAppliedMarker, Runnable]
    [FieldBackedContextStoreAppliedMarker, Runnable, FieldBackedContextStoreAppliedMarker] | [FieldBackedContextStoreAppliedMarker, Runnable]
    [FieldBackedContextStoreAppliedMarker, FieldBackedContextStoreAppliedMarker, Runnable] | [FieldBackedContextStoreAppliedMarker, Runnable]
  }
}
