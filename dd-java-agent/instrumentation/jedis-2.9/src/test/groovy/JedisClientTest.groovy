import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import org.junit.ClassRule
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer
import spock.lang.Shared
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import io.opentracing.tag.Tags
import datadog.trace.api.DDTags

class JedisClientTest extends AgentTestRunner {

  public static final int PORT = 6399

  static {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN)
    ((Logger) LoggerFactory.getLogger("datadog")).setLevel(Level.DEBUG)
    System.setProperty("dd.integration.redis.enabled", "true")
  }
	
  @Shared
  RedisServer redisServer = new RedisServer(PORT);
  @Shared
  Jedis jedis = new Jedis("localhost",PORT);
  
  def setupSpec() {
    redisServer.start();
  }
 
  def cleanupSpec() {
    redisServer.stop();
	jedis.close();
  }
    
  def "set command"() {	
    jedis.set("foo", "bar");		
	
	expect:
	final DDSpan setTrace = TEST_WRITER.get(TEST_WRITER.size() - 1).get(0)
	setTrace.getServiceName() == "redis"
	setTrace.getOperationName() == "redis.query"
	setTrace.getResourceName() == "SET"
	setTrace.getTags().get(Tags.COMPONENT.getKey()) == "redis-command"
	setTrace.getTags().get(Tags.DB_TYPE.getKey()) == "redis"
	setTrace.getTags().get(Tags.SPAN_KIND.getKey()) == "client"
	setTrace.getTags().get(DDTags.SPAN_TYPE) == "redis"
  }
  
  def "get command"() {
    jedis.set("foo", "bar")
    def value = jedis.get("foo")
	  			  
    expect:
    value == "bar"
    final DDSpan setTrace = TEST_WRITER.get(TEST_WRITER.size() - 1).get(0)
    setTrace.getServiceName() == "redis"
    setTrace.getOperationName() == "redis.query"
    setTrace.getResourceName() == "GET"
    setTrace.getTags().get(Tags.COMPONENT.getKey()) == "redis-command"
    setTrace.getTags().get(Tags.DB_TYPE.getKey()) == "redis"
    setTrace.getTags().get(Tags.SPAN_KIND.getKey()) == "client"
    setTrace.getTags().get(DDTags.SPAN_TYPE) == "redis"
  }

  def "command with no arguments"() {		
    jedis.flushAll()
    jedis.set("foo", "bar")
    def value = jedis.randomKey()
				
    expect:
    value == "foo"
    final DDSpan setTrace = TEST_WRITER.get(TEST_WRITER.size() - 1).get(0)
    setTrace.getServiceName() == "redis"
    setTrace.getOperationName() == "redis.query"
    setTrace.getResourceName() == "RANDOMKEY"
    setTrace.getTags().get(Tags.COMPONENT.getKey()) == "redis-command"
    setTrace.getTags().get(Tags.DB_TYPE.getKey()) == "redis"
    setTrace.getTags().get(Tags.SPAN_KIND.getKey()) == "client"
    setTrace.getTags().get(DDTags.SPAN_TYPE) == "redis"
  }
}