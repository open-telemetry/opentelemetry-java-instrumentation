import io.opentelemetry.auto.test.AgentTestRunner
import org.apache.geode.cache.client.ClientCacheFactory
import org.apache.geode.cache.client.ClientRegionShortcut
import spock.lang.Shared
import spock.lang.Unroll

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

@Unroll
class PutGetTest extends AgentTestRunner {
  @Shared
  def cache = new ClientCacheFactory().create()

  @Shared
  def regionFactory = cache.createClientRegionFactory(ClientRegionShortcut.LOCAL)

  @Shared
  def region = regionFactory.create("test-region")

  def "test put and get"() {
    setup:
    region.clear()

    when:
    runUnderTrace("someTrace") {
      region.put(key, value)
    }

    then:
    region.get(key) == value

    where:
    key      | value
    'Hello'  | 'World'
    'Humpty' | 'Dumpty'
    '1'      | 'One'
    'One'    | '1'
  }
}
