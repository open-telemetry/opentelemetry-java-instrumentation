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

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.trace.attributes.SemanticAttributes
import org.apache.geode.cache.client.ClientCacheFactory
import org.apache.geode.cache.client.ClientRegionShortcut
import spock.lang.Shared
import spock.lang.Unroll

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.INTERNAL

@Unroll
class PutGetTest extends AgentTestRunner {
  @Shared
  def cache = new ClientCacheFactory().create()

  @Shared
  def regionFactory = cache.createClientRegionFactory(ClientRegionShortcut.LOCAL)

  @Shared
  def region = regionFactory.create("test-region")

  def "test put and get"() {
    when:
    def cacheValue
    runUnderTrace("someTrace") {
      region.clear()
      region.put(key, value)
      cacheValue = region.get(key)
    }

    then:
    cacheValue == value
    assertGeodeTrace("get", null)

    where:
    key      | value
    'Hello'  | 'World'
    'Humpty' | 'Dumpty'
    '1'      | 'One'
    'One'    | '1'
  }

  def "test put and remove"() {
    when:
    runUnderTrace("someTrace") {
      region.clear()
      region.put(key, value)
      region.remove(key)
    }

    then:
    region.size() == 0
    assertGeodeTrace("remove", null)

    where:
    key      | value
    'Hello'  | 'World'
    'Humpty' | 'Dumpty'
    '1'      | 'One'
    'One'    | '1'
  }

  def "test query"() {
    when:
    def cacheValue
    runUnderTrace("someTrace") {
      region.clear()
      region.put(key, value)
      cacheValue = region.query("SELECT * FROM /test-region")
    }

    then:
    cacheValue.asList().size()
    assertGeodeTrace("query", "SELECT * FROM /test-region")

    where:
    key      | value
    'Hello'  | 'World'
    'Humpty' | 'Dumpty'
    '1'      | 'One'
    'One'    | '1'
  }

  def "test existsValue"() {
    when:
    def exists
    runUnderTrace("someTrace") {
      region.clear()
      region.put(key, value)
      exists = region.existsValue("SELECT * FROM /test-region")
    }

    then:
    exists
    assertGeodeTrace("existsValue", "SELECT * FROM /test-region")

    where:
    key      | value
    'Hello'  | 'World'
    'Humpty' | 'Dumpty'
    '1'      | 'One'
    'One'    | '1'
  }

  def assertGeodeTrace(String verb, String query) {
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          operationName "someTrace"
          spanKind INTERNAL
          errored false
        }
        span(1) {
          operationName "clear"
          spanKind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "geode"
            "${SemanticAttributes.DB_INSTANCE.key()}" "test-region"
          }
        }
        span(2) {
          operationName "put"
          spanKind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "geode"
            "${SemanticAttributes.DB_INSTANCE.key()}" "test-region"
          }
        }
        span(3) {
          operationName verb
          spanKind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.DB_TYPE.key()}" "geode"
            "${SemanticAttributes.DB_INSTANCE.key()}" "test-region"
            if (query != null) {
              "${SemanticAttributes.DB_STATEMENT.key()}" query
            }
          }
        }
      }
    }
    return true
  }
}
