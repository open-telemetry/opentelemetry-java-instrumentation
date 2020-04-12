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

class OrmClientDecoratorTest extends DatabaseClientDecoratorTest {

  def "test spanNameForOperation #testName"() {
    setup:
    decorator = newDecorator({ e -> entityName })

    when:
    def result = decorator.spanNameForOperation("orm", entity)

    then:
    result == spanName

    where:
    testName          | entity     | entityName | spanName
    "null entity"     | null       | "name"     | "orm"
    "null entityName" | "not null" | null       | "orm"
    "name set"        | "not null" | "name"     | "orm name"
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
    }
  }
}
