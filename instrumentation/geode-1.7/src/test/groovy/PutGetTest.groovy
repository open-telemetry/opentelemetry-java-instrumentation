import io.opentelemetry.auto.test.AgentTestRunner
import org.apache.geode.cache.client.ClientCacheFactory
import org.apache.geode.cache.client.ClientRegionShortcut
import spock.lang.Unroll

@Unroll
class PutGetTest extends AgentTestRunner {

  def "test put and get"() {
    setup:
    def cache = ClientCacheFactory.create()
    def regionFactory = cache.createClientRegionFactory(ClientRegionShortcut.LOCAL)
    def region = regionFactory.create("test-region")

    when:
    region.put(key, value)

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
