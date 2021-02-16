/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.geode.DataSerializable
import org.apache.geode.cache.client.ClientCacheFactory
import org.apache.geode.cache.client.ClientRegionShortcut
import spock.lang.Shared
import spock.lang.Unroll

@Unroll
class PutGetTest extends AgentInstrumentationSpecification {
  @Shared
  def cache = new ClientCacheFactory().create()

  @Shared
  def regionFactory = cache.createClientRegionFactory(ClientRegionShortcut.LOCAL)

  @Shared
  def region = regionFactory.create("test-region")

  def "test put and get"() {
    when:
    def cacheValue = runUnderTrace("someTrace") {
      region.clear()
      region.put(key, value)
      region.get(key)
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
    def cacheValue = runUnderTrace("someTrace") {
      region.clear()
      region.put(key, value)
      region.query("SELECT * FROM /test-region")
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
    def exists = runUnderTrace("someTrace") {
      region.clear()
      region.put(key, value)
      region.existsValue("SELECT * FROM /test-region")
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
          name "someTrace"
          kind INTERNAL
          errored false
        }
        span(1) {
          name "clear"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "geode"
            "$SemanticAttributes.DB_NAME.key" "test-region"
            "$SemanticAttributes.DB_OPERATION.key" "clear"
          }
        }
        span(2) {
          name "put"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "geode"
            "$SemanticAttributes.DB_NAME.key" "test-region"
            "$SemanticAttributes.DB_OPERATION.key" "put"
          }
        }
        span(3) {
          name verb
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "geode"
            "$SemanticAttributes.DB_NAME.key" "test-region"
            "$SemanticAttributes.DB_OPERATION.key" verb
            if (query != null) {
              "$SemanticAttributes.DB_STATEMENT.key" query
            }
          }
        }
      }
    }
    return true
  }

  def "should sanitize geode query"() {
    given:
    def value = new Card(cardNumber: '1234432156788765', expDate: '10/2020')

    region.clear()
    region.put(1, value)
    ignoreTracesAndClear(2)

    when:
    def results = region.query("SELECT * FROM /test-region p WHERE p.expDate = '10/2020'")

    then:
    results.toList() == [value]

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "query"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "geode"
            "$SemanticAttributes.DB_NAME.key" "test-region"
            "$SemanticAttributes.DB_OPERATION.key" "query"
            "$SemanticAttributes.DB_STATEMENT.key" "SELECT * FROM /test-region p WHERE p.expDate = ?"
          }
        }
      }
    }
  }

  static class Card implements DataSerializable {
    String cardNumber
    String expDate

    @Override
    void toData(DataOutput dataOutput) throws IOException {
      dataOutput.writeUTF(cardNumber)
      dataOutput.writeUTF(expDate)
    }

    @Override
    void fromData(DataInput dataInput) throws IOException, ClassNotFoundException {
      cardNumber = dataInput.readUTF()
      expDate = dataInput.readUTF()
    }
  }
}
