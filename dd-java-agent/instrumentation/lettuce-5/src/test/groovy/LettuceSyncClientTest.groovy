import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.instrumentation.lettuce.RedisAsyncCommandsInstrumentation
import datadog.trace.instrumentation.lettuce.RedisClientInstrumentation
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.sync.RedisCommands
import redis.embedded.RedisServer
import spock.lang.Shared

class LettuceSyncClientTest extends AgentTestRunner {

  @Shared
  public static final String HOST = "127.0.0.1"
  public static final int PORT = 6399
  public static final int DB_INDEX = 0
  @Shared
  public static final String DB_ADDR = HOST + ":" + PORT + "/" + DB_INDEX
  @Shared
  public static final String DB_ADDR_NON_EXISTENT = HOST + ":" + 9999 + "/" + DB_INDEX
  @Shared
  public static final String DB_URI_NON_EXISTENT = "redis://" + DB_ADDR_NON_EXISTENT
  public static final String EMBEDDED_DB_URI = "redis://" + DB_ADDR

  @Shared
  RedisServer redisServer = RedisServer.builder()
  // bind to localhost to avoid firewall popup
    .setting("bind " + HOST)
  // set max memory to avoid problems in CI
    .setting("maxmemory 128M")
    .port(PORT).build()

  @Shared
  RedisClient redisClient = RedisClient.create(EMBEDDED_DB_URI)

  @Shared
  RedisCommands<String, ?> syncCommands = null

  @Shared
  Map<String, String> testHashMap = [
          firstname: "John",
          lastname:  "Doe",
          age:       "53"
  ]

  def setupSpec() {
    println "Using redis: $redisServer.args"
    redisServer.start()
    StatefulConnection connection = redisClient.connect()
    syncCommands = connection.sync()
  }

  def cleanupSpec() {
    redisServer.stop()
  }

  def setup() {
    TEST_WRITER.start()
  }

  def "connect"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(EMBEDDED_DB_URI)
    testConnectionClient.connect()

    expect:
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]
    span.getServiceName() == RedisClientInstrumentation.SERVICE_NAME
    span.getOperationName() == "redis.query"
    span.getType() == RedisClientInstrumentation.SERVICE_NAME
    span.getResourceName() == RedisClientInstrumentation.RESOURCE_NAME_PREFIX + DB_ADDR
    !span.context().getErrorFlag()

    def tags = span.context().tags
    tags[RedisClientInstrumentation.REDIS_URL_TAGNAME] == DB_ADDR
    tags[RedisClientInstrumentation.REDIS_DB_INDEX_TAG_NAME] == 0
    tags["span.kind"] == "client"
    tags["span.type"] == RedisClientInstrumentation.SERVICE_NAME
    tags["db.type"] == RedisClientInstrumentation.SERVICE_NAME
    tags["component"] == RedisClientInstrumentation.COMPONENT_NAME
    tags["thread.name"] != null
    tags["thread.id"] != null
  }

  def "connect exception"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(DB_URI_NON_EXISTENT)
    try {
      testConnectionClient.connect()
    } catch (RedisConnectionException rce) { }

    expect:
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]
    span.getServiceName() == RedisClientInstrumentation.SERVICE_NAME
    span.getOperationName() == "redis.query"
    span.getType() == RedisClientInstrumentation.SERVICE_NAME
    span.getResourceName() == RedisClientInstrumentation.RESOURCE_NAME_PREFIX + DB_ADDR_NON_EXISTENT
    span.context().getErrorFlag()

    def tags = span.context().tags
    tags[RedisClientInstrumentation.REDIS_URL_TAGNAME] == DB_ADDR_NON_EXISTENT
    tags[RedisClientInstrumentation.REDIS_DB_INDEX_TAG_NAME] == 0
    tags["span.kind"] == "client"
    tags["span.type"] == RedisClientInstrumentation.SERVICE_NAME
    tags["db.type"] == RedisClientInstrumentation.SERVICE_NAME
    tags["component"] == RedisClientInstrumentation.COMPONENT_NAME
    tags["thread.name"] != null
    tags["thread.id"] != null
  }

  def "set command"() {
    setup:
    String res = syncCommands.set("TESTKEY", "TESTVAL")
    TEST_WRITER.waitForTraces(1)

    expect:
    res == "OK"
    TEST_WRITER.size() == 1


    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]
    span.getServiceName() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getOperationName() == "redis.query"
    span.getType() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getResourceName() == "SET"
    !span.context().getErrorFlag()

    def tags = span.context().tags
    tags["span.kind"] == "client"
    tags["span.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.redis.command.args"] == "key<TESTKEY> value<TESTVAL>"
    tags["component"] == RedisAsyncCommandsInstrumentation.COMPONENT_NAME
    tags["thread.name"] != null
    tags["thread.id"] != null
  }

  def "get command"() {
    setup:
    String res = syncCommands.get("TESTKEY")
    TEST_WRITER.waitForTraces(1)

    expect:
    res == "TESTVAL"
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]
    span.getServiceName() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getOperationName() == "redis.query"
    span.getType() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getResourceName() == "GET"
    !span.context().getErrorFlag()

    def tags = span.context().tags
    tags["span.kind"] == "client"
    tags["span.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.redis.command.args"] == "key<TESTKEY>"
    tags["component"] == RedisAsyncCommandsInstrumentation.COMPONENT_NAME
    tags["thread.name"] != null
    tags["thread.id"] != null
  }

  def "get non existent key command"() {
    setup:
    String res = syncCommands.get("NON_EXISTENT_KEY")
    TEST_WRITER.waitForTraces(1)

    expect:
    res == null
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]
    span.getServiceName() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getOperationName() == "redis.query"
    span.getType() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getResourceName() == "GET"
    !span.context().getErrorFlag()

    def tags = span.context().tags
    tags["span.kind"] == "client"
    tags["span.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.redis.command.args"] == "key<NON_EXISTENT_KEY>"
    tags["component"] == RedisAsyncCommandsInstrumentation.COMPONENT_NAME
    tags["thread.name"] != null
    tags["thread.id"] != null
  }

  def "command with no arguments"() {
    setup:
    def keyRetrieved = syncCommands.randomkey()
    TEST_WRITER.waitForTraces(1)

    expect:
    keyRetrieved == "TESTKEY"
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1

    def span = trace[0]
    span.getServiceName() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getOperationName() == "redis.query"
    span.getType() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getResourceName() == "RANDOMKEY"
    !span.context().getErrorFlag()

    def tags = span.context().tags
    tags["span.kind"] == "client"
    tags["span.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.redis.command.args"] == null
    tags["component"] == RedisAsyncCommandsInstrumentation.COMPONENT_NAME
    tags["thread.name"] != null
    tags["thread.id"] != null
  }

  def "list command"() {
    setup:
    long res = syncCommands.lpush("TESTLIST", "TESTLIST ELEMENT")
    TEST_WRITER.waitForTraces(1)

    expect:
    res == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1

    def span = trace[0]
    span.getServiceName() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getOperationName() == "redis.query"
    span.getType() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getResourceName() == "LPUSH"
    !span.context().getErrorFlag()

    def tags = span.context().tags
    tags["span.kind"] == "client"
    tags["span.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.redis.command.args"] == "key<TESTLIST> value<TESTLIST ELEMENT>"
    tags["component"] == RedisAsyncCommandsInstrumentation.COMPONENT_NAME
    tags["thread.name"] != null
    tags["thread.id"] != null
  }

  def "hash set command"() {
    setup:
    def res = syncCommands.hmset("user", testHashMap)
    TEST_WRITER.waitForTraces(1)

    expect:
    res == "OK"
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1

    def span = trace[0]
    span.getServiceName() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getOperationName() == "redis.query"
    span.getType() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getResourceName() == "HMSET"
    !span.context().getErrorFlag()

    def tags = span.context().tags
    tags["span.kind"] == "client"
    tags["span.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.redis.command.args"] == "key<user> key<firstname> value<John> key<lastname> value<Doe> key<age> value<53>"
    tags["component"] == RedisAsyncCommandsInstrumentation.COMPONENT_NAME
    tags["thread.name"] != null
    tags["thread.id"] != null
  }

  def "hash getall command"() {
    setup:
    Map<String, String> res = syncCommands.hgetall("user")
    TEST_WRITER.waitForTraces(1)

    expect:
    res == testHashMap
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1

    def span = trace[0]
    span.getServiceName() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getOperationName() == "redis.query"
    span.getType() == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    span.getResourceName() == "HGETALL"
    !span.context().getErrorFlag()

    def tags = span.context().tags
    tags["span.kind"] == "client"
    tags["span.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.type"] == RedisAsyncCommandsInstrumentation.SERVICE_NAME
    tags["db.redis.command.args"] == "key<user>"
    tags["component"] == RedisAsyncCommandsInstrumentation.COMPONENT_NAME
    tags["thread.name"] != null
    tags["thread.id"] != null
  }
}
