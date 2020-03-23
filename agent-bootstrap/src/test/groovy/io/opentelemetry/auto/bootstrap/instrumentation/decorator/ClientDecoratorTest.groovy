/*
 * Copyright 2020, OpenTelemetry Authors
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

import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.trace.Span

class ClientDecoratorTest extends BaseDecoratorTest {

  def span = Mock(Span)

  def "test afterStart"() {
    setup:
    def decorator = newDecorator((String) serviceName)

    when:
    decorator.afterStart(span)

    then:
    1 * span.setAttribute(MoreTags.SERVICE_NAME, serviceName)
    1 * span.setAttribute(Tags.COMPONENT, "test-component")
    _ * span.setAttribute(_, _) // Want to allow other calls from child implementations.
    0 * _

    where:
    serviceName << ["test-service", "other-service", null]
  }

  def "test beforeFinish"() {
    when:
    newDecorator("test-service").beforeFinish(span)

    then:
    0 * _
  }

  @Override
  def newDecorator() {
    return newDecorator("test-service")
  }

  def newDecorator(String serviceName) {
    return new ClientDecorator() {

      @Override
      protected String service() {
        return serviceName
      }

      @Override
      protected String getComponentName() {
        return "test-component"
      }
    }
  }
}
