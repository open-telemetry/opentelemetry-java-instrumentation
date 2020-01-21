import io.opentelemetry.auto.api.Config
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer
import spock.lang.Shared

class Jedis30ClientTest extends AgentTestRunner {

  @Shared
  int port = PortUtils.randomOpenPort()

  @Shared
  RedisServer redisServer = RedisServer.builder()
  // bind to localhost to avoid firewall popup
    .setting("bind 127.0.0.1")
  // set max memory to avoid problems in CI
    .setting("maxmemory 128M")
    .port(port).build()
  @Shared
  Jedis jedis = new Jedis("localhost", port)

  def setupSpec() {
    println "Using redis: $redisServer.args"
    redisServer.start()

    // This setting should have no effect since decorator returns null for the instance.
    System.setProperty(Config.PREFIX + Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "true")
  }

  def cleanupSpec() {
    redisServer.stop()
    jedis.close()

    System.clearProperty(Config.PREFIX + Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE)
  }

  def setup() {
    jedis.flushAll()
    TEST_WRITER.clear()
  }

  def "set command"() {
    when:
    jedis.set("foo", "bar")

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "redis.query"
          tags {
            "$MoreTags.SERVICE_NAME" "redis"
            "$MoreTags.SPAN_TYPE" SpanTypes.REDIS
            "$Tags.COMPONENT" "redis-command"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "redis"
            "$Tags.DB_STATEMENT" "SET"
          }
        }
      }
    }
  }

  def "get command"() {
    when:
    jedis.set("foo", "bar")
    def value = jedis.get("foo")

    then:
    value == "bar"

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "redis.query"
          tags {
            "$MoreTags.SERVICE_NAME" "redis"
            "$MoreTags.SPAN_TYPE" SpanTypes.REDIS
            "$Tags.COMPONENT" "redis-command"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "redis"
            "$Tags.DB_STATEMENT" "SET"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "redis.query"
          tags {
            "$MoreTags.SERVICE_NAME" "redis"
            "$MoreTags.SPAN_TYPE" SpanTypes.REDIS
            "$Tags.COMPONENT" "redis-command"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "redis"
            "$Tags.DB_STATEMENT" "GET"
          }
        }
      }
    }
  }

  def "command with no arguments"() {
    when:
    jedis.set("foo", "bar")
    def value = jedis.randomKey()

    then:
    value == "foo"

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "redis.query"
          tags {
            "$MoreTags.SERVICE_NAME" "redis"
            "$MoreTags.SPAN_TYPE" SpanTypes.REDIS
            "$Tags.COMPONENT" "redis-command"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "redis"
            "$Tags.DB_STATEMENT" "SET"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "redis.query"
          tags {
            "$MoreTags.SERVICE_NAME" "redis"
            "$MoreTags.SPAN_TYPE" SpanTypes.REDIS
            "$Tags.COMPONENT" "redis-command"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "redis"
            "$Tags.DB_STATEMENT" "RANDOMKEY"
          }
        }
      }
    }
  }
}
