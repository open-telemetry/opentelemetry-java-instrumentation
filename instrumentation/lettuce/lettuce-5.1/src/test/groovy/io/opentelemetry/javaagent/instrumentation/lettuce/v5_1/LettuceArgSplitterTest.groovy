/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1

import spock.lang.Specification
import spock.lang.Unroll

class LettuceArgSplitterTest extends Specification {
  @Unroll
  def "should properly split #desc"() {
    expect:
    LettuceArgSplitter.splitArgs(args) == result

    where:
    desc                     | args                                     | result
    "a null value"           | null                                     | []
    "an empty value"         | ""                                       | []
    "a single key"           | "key<key>"                               | ["key"]
    "a single value"         | "value<value>"                           | ["value"]
    "a plain string"         | "teststring"                             | ["teststring"]
    "an integer"             | "42"                                     | ["42"]
    "a base64 value"         | "TeST123=="                              | ["TeST123=="]
    "a complex list of args" | "key<key> aSDFgh4321= 5 test value<val>" | ["key", "aSDFgh4321=", "5", "test", "val"]
  }
}
