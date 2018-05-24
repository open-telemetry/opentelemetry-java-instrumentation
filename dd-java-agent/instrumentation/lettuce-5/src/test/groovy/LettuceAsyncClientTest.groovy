import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.instrumentation.lettuce.RedisAsyncCommandsInstrumentation
import datadog.trace.instrumentation.lettuce.RedisClientInstrumentation
import io.lettuce.core.ConnectionFuture
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisFuture
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.StringCodec
import redis.embedded.RedisServer
import spock.lang.Shared
import spock.util.concurrent.AsyncConditions

import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

class LettuceAsyncClientTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.redis.enabled", "true")
  }

  @Shared
  public static final String HOST = "127.0.0.1"
  public static final int PORT = 6399
  public static final int INCORRECT_PORT = 9999
  public static final int DB_INDEX = 0
  @Shared
  public static final String DB_ADDR = HOST + ":" + PORT + "/" + DB_INDEX
  @Shared
  public static final String DB_ADDR_NON_EXISTENT = HOST + ":" + INCORRECT_PORT + "/" + DB_INDEX
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
  RedisAsyncCommands<String, ?> asyncCommands = null

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
    asyncCommands = connection.async()
  }

  def cleanupSpec() {
    redisServer.stop()
  }

  def setup() {
    TEST_WRITER.start()
  }

  def "connect using get on ConnectionFuture"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(EMBEDDED_DB_URI)
    ConnectionFuture connectionFuture = testConnectionClient.connectAsync(StringCodec.UTF8,
      new RedisURI(HOST, PORT, 3, TimeUnit.SECONDS))
    def connection = connectionFuture.get()
    TEST_WRITER.waitForTraces(1)

    expect:
    connection != null
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
    tags[RedisClientInstrumentation.REDIS_URL_TAG_NAME] == DB_ADDR
    tags[RedisClientInstrumentation.REDIS_DB_INDEX_TAG_NAME] == 0
    tags["span.kind"] == "client"
    tags["span.type"] == RedisClientInstrumentation.SERVICE_NAME
    tags["db.type"] == RedisClientInstrumentation.SERVICE_NAME
    tags["component"] == RedisClientInstrumentation.COMPONENT_NAME
    tags["thread.name"] != null
    tags["thread.id"] != null
  }

  def "connect exception inside the connection future"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(DB_URI_NON_EXISTENT)
    StatefulConnection connection = null
    try {
      ConnectionFuture connectionFuture = testConnectionClient.connectAsync(StringCodec.UTF8,
        new RedisURI(HOST, INCORRECT_PORT, 3, TimeUnit.SECONDS))
      connection = connectionFuture.get()
    } catch (Exception rce) {
      // do nothing, this is expected
      println("caught " + rce.getMessage())
    }

    expect:
    TEST_WRITER.waitForTraces(1)
    connection == null
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
    tags[RedisClientInstrumentation.REDIS_URL_TAG_NAME] == DB_ADDR_NON_EXISTENT
    tags[RedisClientInstrumentation.REDIS_DB_INDEX_TAG_NAME] == 0
    tags["span.kind"] == "client"
    tags["span.type"] == RedisClientInstrumentation.SERVICE_NAME
    tags["db.type"] == RedisClientInstrumentation.SERVICE_NAME
    tags["component"] == RedisClientInstrumentation.COMPONENT_NAME
    tags["thread.name"] != null
    tags["thread.id"] != null
  }

  def "set command using Future get with timeout"() {
    setup:
    RedisFuture<String> redisFuture = asyncCommands.set("TESTKEY", "TESTVAL")
    String res = redisFuture.get(3, TimeUnit.SECONDS)
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

  def "get command chained with thenAccept"() {
    setup:
    def conds = new AsyncConditions()
    Consumer<String> consumer = new Consumer<String>() {
      @Override
      void accept(String res) {
        conds.evaluate {
          assert res == "TESTVAL"
        }
      }
    }

    when:
    RedisFuture<String> redisFuture = asyncCommands.get("TESTKEY")
    redisFuture.thenAccept(consumer)

    then:
    conds.await()
    TEST_WRITER.waitForTraces(1)
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

  // to make sure instrumentation's chained completion stages won't interfere with user's, while still
  // recording metrics
  def "get non existent key command with handleAsync and chained with thenApply"() {
    setup:
    def conds = new AsyncConditions()
    final String SUCCESS = "KEY MISSING"
    BiFunction<String, Throwable, String> firstStage = new BiFunction<String, Throwable, String>() {
      @Override
      String apply(String res, Throwable throwable) {
        conds.evaluate{
          assert res == null
          assert throwable == null
        }
        return (res == null ? SUCCESS : res)
      }
    }
    Function<String, Object> secondStage = new Function<String, Object>() {
      @Override
      Object apply(String input) {
        conds.evaluate{
          assert input == SUCCESS
        }
        return null
      }
    }

    when:
    RedisFuture<String> redisFuture = asyncCommands.get("NON_EXISTENT_KEY")
    redisFuture.handleAsync(firstStage).thenApply(secondStage)

    then:
    conds.await()
    TEST_WRITER.waitForTraces(1)
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

  def "command with no arguments using a biconsumer"() {
    setup:
    def conds = new AsyncConditions()
    BiConsumer<String, Throwable> biConsumer = new BiConsumer<String, Throwable>() {
      @Override
      void accept(String keyRetrieved, Throwable throwable) {
        conds.evaluate{
          assert keyRetrieved == "TESTKEY"
        }
      }
    }

    when:
    RedisFuture<String> redisFuture = asyncCommands.randomkey()
    redisFuture.whenCompleteAsync(biConsumer)

    then:
    conds.await()
    TEST_WRITER.waitForTraces(1)
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

  def "hash set and then nest apply to hash getall"() {
    setup:
    def conds = new AsyncConditions()

    when:
    RedisFuture<String> hmsetFuture = asyncCommands.hmset("user", testHashMap)
    hmsetFuture.thenApplyAsync(new Function<String, Object>() {
      @Override
      Object apply(String setResult) {
        TEST_WRITER.waitForTraces(1)
        TEST_WRITER.start()
        conds.evaluate {
          assert setResult == "OK"
        }
        RedisFuture<Map<String, String>> hmGetAllFuture = asyncCommands.hgetall("user")
        hmGetAllFuture.exceptionally(new Function<Throwable, Map<String, String>>() {
          @Override
          Map<String, String> apply(Throwable throwable) {
            println("unexpected:" + throwable.toString())
            throwable.printStackTrace()
            assert false
            return null
          }
        })
        hmGetAllFuture.thenAccept(new Consumer<Map<String, String>>() {
          @Override
          void accept(Map<String, String> hmGetAllResult) {
            conds.evaluate {
              assert testHashMap == hmGetAllResult
            }
          }
        })
        return null
      }
    })

    then:
    conds.await()
    TEST_WRITER.waitForTraces(1)
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
