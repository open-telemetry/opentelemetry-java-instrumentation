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

import io.opentelemetry.auto.config.Config
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.trace.Span

import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride

class DatabaseClientDecoratorTest extends ClientDecoratorTest {

  def span = Mock(Span)

  def "test afterStart"() {
    setup:
    def decorator = newDecorator((String) serviceName)

    when:
    decorator.afterStart(span)

    then:
    1 * span.setAttribute(MoreTags.SERVICE_NAME, serviceName)
    1 * span.setAttribute(Tags.COMPONENT, "test-component")
    1 * span.setAttribute(Tags.DB_TYPE, "test-db")
    0 * _

    where:
    serviceName << ["test-service", "other-service", null]
  }

  def "test onConnection"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "$renameService") {
      decorator.onConnection(span, session)
    }

    then:
    if (session) {
      1 * span.setAttribute(Tags.DB_USER, session.user)
      1 * span.setAttribute(Tags.DB_INSTANCE, session.instance)
      1 * span.setAttribute(Tags.DB_URL, session.url)
      if (renameService) {
        1 * span.setAttribute(MoreTags.SERVICE_NAME, session.instance)
      }
    }
    0 * _

    where:
    renameService | session
    false         | null
    true          | [user: "test-user"]
    false         | [instance: "test-instance", url: "test:"]
    true          | [user: "test-user", instance: "test-instance"]
  }

  def "test onStatement"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onStatement(span, statement)

    then:
    1 * span.setAttribute(Tags.DB_STATEMENT, statement)
    0 * _

    where:
    statement      | _
    null           | _
    ""             | _
    "db-statement" | _
  }

  def "test assert null span"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.afterStart((Span) null)

    then:
    thrown(AssertionError)

    when:
    decorator.onConnection((Span) null, null)

    then:
    thrown(AssertionError)

    when:
    decorator.onStatement((Span) null, null)

    then:
    thrown(AssertionError)
  }

  @Override
  def newDecorator(String serviceName = "test-service") {
    return new DatabaseClientDecorator<Map>() {

      @Override
      protected String service() {
        return serviceName
      }

      @Override
      protected String getComponentName() {
        return "test-component"
      }

      @Override
      protected String dbType() {
        return "test-db"
      }

      @Override
      protected String dbUser(Map map) {
        return map.user
      }

      @Override
      protected String dbInstance(Map map) {
        return map.instance
      }

      @Override
      protected String dbUrl(Map map) {
        return map.url
      }
    }
  }
}
