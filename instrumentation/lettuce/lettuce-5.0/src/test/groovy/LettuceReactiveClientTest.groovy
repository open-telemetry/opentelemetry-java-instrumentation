/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.lettuce.core.api.sync.RedisCommands
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.trace.attributes.SemanticAttributes
import io.opentelemetry.trace.attributes.StringAttributeSetter
import reactor.core.scheduler.Schedulers
import redis.embedded.RedisServer
import spock.lang.Shared
import spock.util.concurrent.AsyncConditions

import java.util.function.Consumer

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

class LettuceReactiveClientTest extends AgentTestRunner {
    public static final String PEER_HOST = "localhost"
    public static final String PEER_IP = "127.0.0.1"
    public static final int DB_INDEX = 0
    // Disable autoreconnect so we do not get stray traces popping up on server shutdown
    public static final ClientOptions CLIENT_OPTIONS = ClientOptions.builder().autoReconnect(false).build()

    @Shared
    String embeddedDbUri

    @Shared
    RedisServer redisServer

    RedisClient redisClient
    StatefulConnection connection
    RedisReactiveCommands<String, ?> reactiveCommands
    RedisCommands<String, ?> syncCommands

    def setupSpec() {
        int port = PortUtils.randomOpenPort()
        String dbAddr = PEER_HOST + ":" + port + "/" + DB_INDEX
        embeddedDbUri = "redis://" + dbAddr

        redisServer = RedisServer.builder()
        // bind to localhost to avoid firewall popup
                .setting("bind " + PEER_HOST)
        // set max memory to avoid problems in CI
                .setting("maxmemory 128M")
                .port(port).build()
    }

    def setup() {
        redisClient = RedisClient.create(embeddedDbUri)

        println "Using redis: $redisServer.args"
        redisServer.start()
        redisClient.setOptions(CLIENT_OPTIONS)

        connection = redisClient.connect()
        reactiveCommands = connection.reactive()
        syncCommands = connection.sync()

        syncCommands.set("TESTKEY", "TESTVAL")

        // 1 set + 1 connect trace
        TEST_WRITER.waitForTraces(2)
        TEST_WRITER.clear()
    }

    def cleanup() {
        connection.close()
        redisClient.shutdown()
        redisServer.stop()
    }

    def "set command with subscribe on a defined consumer"() {
        setup:
        def conds = new AsyncConditions()
        Consumer<String> consumer = new Consumer<String>() {
            @Override
            void accept(String res) {
                conds.evaluate {
                    assert res == "OK"
                }
            }
        }

        when:
        reactiveCommands.set("TESTSETKEY", "TESTSETVAL").subscribe(consumer)

        then:
        conds.await()
        assertTraces(1) {
            trace(0, 1) {
                span(0) {
                    operationName "SET"
                    spanKind CLIENT
                    errored false
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "SET"
                    }
                }
            }
        }
    }

    def "get command with lambda function"() {
        setup:
        def conds = new AsyncConditions()

        when:
        reactiveCommands.get("TESTKEY").subscribe { res -> conds.evaluate { assert res == "TESTVAL" } }

        then:
        conds.await()
        assertTraces(1) {
            trace(0, 1) {
                span(0) {
                    operationName "GET"
                    spanKind CLIENT
                    errored false
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "GET"
                    }
                }
            }
        }
    }

    // to make sure instrumentation's chained completion stages won't interfere with user's, while still
    // recording metrics
    def "get non existent key command"() {
        setup:
        def conds = new AsyncConditions()
        final defaultVal = "NOT THIS VALUE"

        when:
        reactiveCommands.get("NON_EXISTENT_KEY").defaultIfEmpty(defaultVal).subscribe {
            res ->
                conds.evaluate {
                    assert res == defaultVal
                }
        }

        then:
        conds.await()
        assertTraces(1) {
            trace(0, 1) {
                span(0) {
                    operationName "GET"
                    spanKind CLIENT
                    errored false
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "GET"
                    }
                }
            }
        }

    }

    def "command with no arguments"() {
        setup:
        def conds = new AsyncConditions()

        when:
        reactiveCommands.randomkey().subscribe {
            res ->
                conds.evaluate {
                    assert res == "TESTKEY"
                }
        }

        then:
        conds.await()
        assertTraces(1) {
            trace(0, 1) {
                span(0) {
                    operationName "RANDOMKEY"
                    spanKind CLIENT
                    errored false
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "RANDOMKEY"
                    }
                }
            }
        }
    }

    def "command flux publisher "() {
        setup:
        reactiveCommands.command().subscribe()

        expect:
        assertTraces(1) {
            trace(0, 1) {
                span(0) {
                    operationName "COMMAND"
                    spanKind CLIENT
                    errored false
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "COMMAND"
                        "db.command.results.count" 157
                    }
                }
            }
        }
    }

    def "command cancel after 2 on flux publisher "() {
        setup:
        reactiveCommands.command().take(2).subscribe()

        expect:
        assertTraces(1) {
            trace(0, 1) {
                span(0) {
                    operationName "COMMAND"
                    spanKind CLIENT
                    errored false
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "COMMAND"
                        "db.command.cancelled" true
                        "db.command.results.count" 2
                    }
                }
            }
        }
    }

    def "non reactive command should not produce span"() {
        setup:
        String res = null

        when:
        res = reactiveCommands.digest(null)

        then:
        res != null
        TEST_WRITER.traces.size() == 0
    }

    def "debug segfault command (returns mono void) with no argument should produce span"() {
        setup:
        reactiveCommands.debugSegfault().subscribe()

        expect:
        assertTraces(1) {
            trace(0, 1) {
                span(0) {
                    operationName "DEBUG"
                    spanKind CLIENT
                    errored false
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "DEBUG"
                    }
                }
            }
        }
    }

    def "shutdown command (returns void) with argument should produce span"() {
        setup:
        reactiveCommands.shutdown(false).subscribe()

        expect:
        assertTraces(1) {
            trace(0, 1) {
                span(0) {
                    operationName "SHUTDOWN"
                    spanKind CLIENT
                    errored false
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "SHUTDOWN"
                    }
                }
            }
        }
    }

    def "blocking subscriber"() {
        when:
        runUnderTrace("test-parent") {
            reactiveCommands.set("a", "1")
                    .then(reactiveCommands.get("a"))
                    .block()
        }

        then:
        assertTraces(1) {
            trace(0, 3) {
                span(0) {
                    operationName "test-parent"
                    errored false
                    attributes {
                    }
                }
                span(1) {
                    operationName "SET"
                    spanKind CLIENT
                    errored false
                    childOf span(0)
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "SET"
                    }
                }
                span(2) {
                    operationName "GET"
                    spanKind CLIENT
                    errored false
                    childOf span(0)
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "GET"
                    }
                }
            }
        }
    }

    def "async subscriber"() {
        when:
        runUnderTrace("test-parent") {
            reactiveCommands.set("a", "1")
                    .then(reactiveCommands.get("a"))
                    .subscribe()
        }

        then:
        assertTraces(1) {
            trace(0, 3) {
                span(0) {
                    operationName "test-parent"
                    errored false
                    attributes {
                    }
                }
                span(1) {
                    operationName "SET"
                    spanKind CLIENT
                    errored false
                    childOf span(0)
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "SET"
                    }
                }
                span(2) {
                    operationName "GET"
                    spanKind CLIENT
                    errored false
                    childOf span(0)
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "GET"
                    }
                }
            }
        }
    }

    def "async subscriber with specific thread pool"() {
        when:
        runUnderTrace("test-parent") {
            reactiveCommands.set("a", "1")
                    .then(reactiveCommands.get("a"))
                    .subscribeOn(Schedulers.elastic())
                    .subscribe()
        }

        then:
        assertTraces(1) {
            trace(0, 3) {
                span(0) {
                    operationName "test-parent"
                    errored false
                    attributes {
                    }
                }
                span(1) {
                    operationName "SET"
                    spanKind CLIENT
                    errored false
                    childOf span(0)
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "SET"
                    }
                }
                span(2) {
                    operationName "GET"
                    spanKind CLIENT
                    errored false
                    childOf span(0)
                    attributes {
                        "${StringAttributeSetter.create("db.system").key()}" "redis"
                        "${SemanticAttributes.DB_STATEMENT.key()}" "GET"
                    }
                }
            }
        }
    }
}
