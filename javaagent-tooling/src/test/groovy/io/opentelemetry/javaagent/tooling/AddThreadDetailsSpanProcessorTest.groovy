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

package io.opentelemetry.javaagent.tooling

import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.trace.attributes.SemanticAttributes
import spock.lang.Specification

class AddThreadDetailsSpanProcessorTest extends Specification {
  def span = Mock(ReadWriteSpan)

  def processor = new AddThreadDetailsSpanProcessor()

  def "should require onStart call"() {
    expect:
    processor.isStartRequired()
  }

  def "should set thread attributes on span start"() {
    given:
    def currentThreadName = Thread.currentThread().name
    def currentThreadId = Thread.currentThread().id

    when:
    processor.onStart(span)

    then:
    1 * span.setAttribute(SemanticAttributes.THREAD_ID.key(), currentThreadId)
    1 * span.setAttribute(SemanticAttributes.THREAD_NAME.key(), currentThreadName)
  }
}
