/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1

import spock.lang.Specification
import spock.lang.Unroll

class RedisCommandNormalizerTest extends Specification {
  @Unroll
  def "should normalise #expected"() {
    when:
    def normalised = RedisCommandNormalizer.normalize(command, args)

    then:
    normalised == expected

    where:
    command            | args                                                          | expected
    // Connection
    "AUTH"             | ["password"]                                                  | "AUTH ?"
    "HELLO"            | ["3", "AUTH", "username", "password"]                         | "HELLO 3 AUTH ? ?"
    "HELLO"            | ["3"]                                                         | "HELLO 3"
    // Hashes
    "HMSET"            | ["hash", "key1", "value1", "key2", "value2"]                  | "HMSET hash key1 ? key2 ?"
    "HSET"             | ["hash", "key1", "value1", "key2", "value2"]                  | "HSET hash key1 ? key2 ?"
    "HSETNX"           | ["hash", "key", "value"]                                      | "HSETNX hash key ?"
    // HyperLogLog
    "PFADD"            | ["hll", "a", "b", "c"]                                        | "PFADD hll ? ? ?"
    // Keys
    "MIGRATE"          | ["127.0.0.1", "4242", "key", "0", "5000", "AUTH", "password"] | "MIGRATE 127.0.0.1 4242 key 0 5000 AUTH ?"
    // Lists
    "LINSERT"          | ["list", "BEFORE", "value1", "value2"]                        | "LINSERT list BEFORE ? ?"
    "LPOS"             | ["list", "value"]                                             | "LPOS list ?"
    "LPUSH"            | ["list", "value1", "value2"]                                  | "LPUSH list ? ?"
    "LPUSHX"           | ["list", "value1", "value2"]                                  | "LPUSHX list ? ?"
    "LREM"             | ["list", "2", "value"]                                        | "LREM list ? ?"
    "LSET"             | ["list", "2", "value"]                                        | "LSET list ? ?"
    "RPUSH"            | ["list", "value1", "value2"]                                  | "RPUSH list ? ?"
    "RPUSHX"           | ["list", "value1", "value2"]                                  | "RPUSHX list ? ?"
    // Pub/Sub
    "PUBLISH"          | ["channel", "message"]                                        | "PUBLISH channel ?"
    // Scripting
    "EVAL"             | ["script", "2", "key1", "key2", "value"]                      | "EVAL script 2 key1 key2 ?"
    "EVALSHA"          | ["sha1", "0", "value1", "value2"]                             | "EVALSHA sha1 0 ? ?"
    // Sets
    "SADD"             | ["set", "value1", "value2"]                                   | "SADD set ? ?"
    "SISMEMBER"        | ["set", "value"]                                              | "SISMEMBER set ?"
    "SMISMEMBER"       | ["set", "value1", "value2"]                                   | "SMISMEMBER set ? ?"
    "SMOVE"            | ["set1", "set2", "value"]                                     | "SMOVE set1 set2 ?"
    "SREM"             | ["set", "value1", "value2"]                                   | "SREM set ? ?"
    // Sorted Sets
    "ZADD"             | ["sset", "1", "value1", "2", "value2"]                        | "ZADD sset ? ? ? ?"
    "ZCOUNT"           | ["sset", "1", "10"]                                           | "ZCOUNT sset ? ?"
    "ZINCRBY"          | ["sset", "1", "value"]                                        | "ZINCRBY sset ? ?"
    "ZLEXCOUNT"        | ["sset", "1", "10"]                                           | "ZLEXCOUNT sset ? ?"
    "ZMSCORE"          | ["sset", "value1", "value2"]                                  | "ZMSCORE sset ? ?"
    "ZRANGEBYLEX"      | ["sset", "1", "10"]                                           | "ZRANGEBYLEX sset ? ?"
    "ZRANGEBYSCORE"    | ["sset", "1", "10"]                                           | "ZRANGEBYSCORE sset ? ?"
    "ZRANK"            | ["sset", "value"]                                             | "ZRANK sset ?"
    "ZREM"             | ["sset", "value1", "value2"]                                  | "ZREM sset ? ?"
    "ZREMRANGEBYLEX"   | ["sset", "1", "10"]                                           | "ZREMRANGEBYLEX sset ? ?"
    "ZREMRANGEBYSCORE" | ["sset", "1", "10"]                                           | "ZREMRANGEBYSCORE sset ? ?"
    "ZREVRANGEBYLEX"   | ["sset", "1", "10"]                                           | "ZREVRANGEBYLEX sset ? ?"
    "ZREVRANGEBYSCORE" | ["sset", "1", "10"]                                           | "ZREVRANGEBYSCORE sset ? ?"
    "ZREVRANK"         | ["sset", "value"]                                             | "ZREVRANK sset ?"
    "ZSCORE"           | ["sset", "value"]                                             | "ZSCORE sset ?"
    // Streams
    "XADD"             | ["stream", "*", "key1", "value1", "key2", "value2"]           | "XADD stream * key1 ? key2 ?"
    // Strings
    "APPEND"           | ["key", "value"]                                              | "APPEND key ?"
    "GETSET"           | ["key", "value"]                                              | "GETSET key ?"
    "MSET"             | ["key1", "value1", "key2", "value2"]                          | "MSET key1 ? key2 ?"
    "MSETNX"           | ["key1", "value1", "key2", "value2"]                          | "MSETNX key1 ? key2 ?"
    "PSETEX"           | ["key", "10000", "value"]                                     | "PSETEX key 10000 ?"
    "SET"              | ["key", "value"]                                              | "SET key ?"
    "SETEX"            | ["key", "10", "value"]                                        | "SETEX key 10 ?"
    "SETNX"            | ["key", "value"]                                              | "SETNX key ?"
    "SETRANGE"         | ["key", "42", "value"]                                        | "SETRANGE key ? ?"
  }

  def "should not normalise any other command"() {
    when:
    def normalised = RedisCommandNormalizer.normalize("MGET", ["key1", "key2"])

    then:
    normalised == "MGET key1 key2"
  }
}
