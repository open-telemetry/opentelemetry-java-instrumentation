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
import io.opentelemetry.trace.Span

class OrmClientDecoratorTest extends DatabaseClientDecoratorTest {

  def "test onOperation #testName"() {
    setup:
    decorator = newDecorator({ e -> entityName })

    when:
    decorator.onOperation(span, entity)

    then:
    if (isSet) {
      1 * span.setAttribute(MoreTags.RESOURCE_NAME, entityName)
    }
    0 * _

    where:
    testName          | entity     | entityName | isSet
    "null entity"     | null       | "name"     | false
    "null entityName" | "not null" | null       | false
    "name set"        | "not null" | "name"     | true
  }

  def "test onOperation null span"() {
    setup:
    decorator = newDecorator({ e -> null })

    when:
    decorator.onOperation((Span) null, null)

    then:
    thrown(AssertionError)
  }


  def newDecorator(name) {
    return new OrmClientDecorator() {
      @Override
      String entityName(Object entity) {
        return name.call(entity)
      }

      @Override
      protected String dbType() {
        return "test-db"
      }

      @Override
      protected String dbUser(Object o) {
        return "test-user"
      }

      @Override
      protected String dbInstance(Object o) {
        return "test-user"
      }

      @Override
      protected String service() {
        return "test-service"
      }

      @Override
      protected String getComponentName() {
        return "test-component"
      }
    }
  }

}
