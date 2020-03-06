import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
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
    String cacheValue
    runUnderTrace("someTrace") {
      region.clear()
      region.put(key, value)
      cacheValue = region.get(key)
    }

    then:
    cacheValue == value
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
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.CACHE
            "$Tags.COMPONENT" "apache-geode-client"
            "$MoreTags.SERVICE_NAME" "apache-geode"
            "$Tags.DB_TYPE" "geode"
          }
        }
        span(2) {
          operationName "put"
          spanKind CLIENT
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.CACHE
            "$Tags.COMPONENT" "apache-geode-client"
            "$MoreTags.SERVICE_NAME" "apache-geode"
            "$Tags.DB_TYPE" "geode"
          }
        }
        span(3) {
          operationName "get"
          spanKind CLIENT
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.CACHE
            "$Tags.COMPONENT" "apache-geode-client"
            "$MoreTags.SERVICE_NAME" "apache-geode"
            "$Tags.DB_TYPE" "geode"
          }
        }
      }
    }


    where:
    key      | value
    'Hello'  | 'World'
    'Humpty' | 'Dumpty'
    '1'      | 'One'
    'One'    | '1'
  }
}
