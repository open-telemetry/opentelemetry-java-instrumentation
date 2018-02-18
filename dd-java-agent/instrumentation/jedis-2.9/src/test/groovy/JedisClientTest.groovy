import org.slf4j.LoggerFactory

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer
import spock.lang.Shared
import datadog.trace.instrumentation.jedis.JedisInstrumentation

class JedisClientTest extends AgentTestRunner {

  public static final int PORT = 6399

  static {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN)
    ((Logger) LoggerFactory.getLogger("datadog")).setLevel(Level.DEBUG)
    System.setProperty("dd.integration.redis.enabled", "true")
  }
	
  @Shared
  RedisServer redisServer = new RedisServer(PORT)
  @Shared
  Jedis jedis = new Jedis("localhost",PORT)
  
  def setupSpec() {
    redisServer.start()
  }
 
  def cleanupSpec() {
    redisServer.stop()
	jedis.close()
  }
    
  def "set command"() {	
    jedis.set("foo", "bar")		
	
    expect:
    final DDSpan setTrace = TEST_WRITER.get(TEST_WRITER.size() - 1).get(0)
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
    final DDSpan setTrace = TEST_WRITER.get(TEST_WRITER.size() - 1).get(0)
    setTrace.getServiceName() == JedisInstrumentation.SERVICE_NAME
    setTrace.getOperationName() == "redis.query"
    setTrace.getResourceName() == "GET"
    setTrace.getTags().get(Tags.COMPONENT.getKey()) == JedisInstrumentation.COMPONENT_NAME
    setTrace.getTags().get(Tags.DB_TYPE.getKey()) == JedisInstrumentation.SERVICE_NAME
    setTrace.getTags().get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_CLIENT
    setTrace.getTags().get(DDTags.SPAN_TYPE) == JedisInstrumentation.SERVICE_NAME
  }

  def "command with no arguments"() {		
    jedis.flushAll()
    jedis.set("foo", "bar")
    def value = jedis.randomKey()
				
    expect:
    value == "foo"
    final DDSpan setTrace = TEST_WRITER.get(TEST_WRITER.size() - 1).get(0)
    setTrace.getServiceName() == JedisInstrumentation.SERVICE_NAME
    setTrace.getOperationName() == "redis.query"
    setTrace.getResourceName() == "RANDOMKEY"
    setTrace.getTags().get(Tags.COMPONENT.getKey()) == JedisInstrumentation.COMPONENT_NAME
    setTrace.getTags().get(Tags.DB_TYPE.getKey()) == JedisInstrumentation.SERVICE_NAME
    setTrace.getTags().get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_CLIENT
    setTrace.getTags().get(DDTags.SPAN_TYPE) == JedisInstrumentation.SERVICE_NAME
  }
}