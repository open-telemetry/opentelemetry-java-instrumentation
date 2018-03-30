import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDTags
import datadog.trace.instrumentation.jedis.JedisInstrumentation
import io.opentracing.tag.Tags
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class JedisClientTest extends AgentTestRunner {

  public static final int PORT = 6399

  @Shared
  RedisServer redisServer = RedisServer.builder()
  // bind to localhost to avoid firewall popup
    .setting("bind 127.0.0.1")
  // set max memory to avoid problems in CI
    .setting("maxmemory 128M")
    .port(PORT).build()
  @Shared
  Jedis jedis = new Jedis("localhost", PORT)

  def setupSpec() {
    println "Using redis: $redisServer.args"
    redisServer.start()
  }

  def cleanupSpec() {
    redisServer.stop()
//    jedis.close()  // not available in the early version we're using.
  }

  def setup() {
    jedis.flushAll()
    TEST_WRITER.start()
  }

  def "set command"() {
    jedis.set("foo", "bar")

    expect:
    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    final DDSpan setTrace = trace.get(0)
    setTrace.getServiceName() == JedisInstrumentation.SERVICE_NAME
    setTrace.getOperationName() == "redis.query"
    setTrace.getResourceName() == "SET"
    setTrace.getTags().get(Tags.COMPONENT.getKey()) == JedisInstrumentation.COMPONENT_NAME
    setTrace.getTags().get(Tags.DB_TYPE.getKey()) == JedisInstrumentation.SERVICE_NAME
    setTrace.getTags().get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_CLIENT
    setTrace.getTags().get(DDTags.SPAN_TYPE) == JedisInstrumentation.SERVICE_NAME
  }

  def "get command"() {
    jedis.set("foo", "bar")
    def value = jedis.get("foo")

    expect:
    value == "bar"
    TEST_WRITER.size() == 2
    def trace = TEST_WRITER.get(1)
    trace.size() == 1
    final DDSpan getSpan = trace.get(0)
    getSpan.getServiceName() == JedisInstrumentation.SERVICE_NAME
    getSpan.getOperationName() == "redis.query"
    getSpan.getResourceName() == "GET"
    getSpan.getTags().get(Tags.COMPONENT.getKey()) == JedisInstrumentation.COMPONENT_NAME
    getSpan.getTags().get(Tags.DB_TYPE.getKey()) == JedisInstrumentation.SERVICE_NAME
    getSpan.getTags().get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_CLIENT
    getSpan.getTags().get(DDTags.SPAN_TYPE) == JedisInstrumentation.SERVICE_NAME
  }

  def "command with no arguments"() {
    jedis.set("foo", "bar")
    def value = jedis.randomKey()

    expect:
    value == "foo"
    TEST_WRITER.size() == 2
    def trace = TEST_WRITER.get(1)
    trace.size() == 1
    final DDSpan randomKeySpan = trace.get(0)
    randomKeySpan.getServiceName() == JedisInstrumentation.SERVICE_NAME
    randomKeySpan.getOperationName() == "redis.query"
    randomKeySpan.getResourceName() == "RANDOMKEY"
    randomKeySpan.getTags().get(Tags.COMPONENT.getKey()) == JedisInstrumentation.COMPONENT_NAME
    randomKeySpan.getTags().get(Tags.DB_TYPE.getKey()) == JedisInstrumentation.SERVICE_NAME
    randomKeySpan.getTags().get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_CLIENT
    randomKeySpan.getTags().get(DDTags.SPAN_TYPE) == JedisInstrumentation.SERVICE_NAME
  }
}
