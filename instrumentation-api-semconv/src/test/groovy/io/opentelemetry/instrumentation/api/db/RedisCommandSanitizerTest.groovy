/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db

import spock.lang.Specification
import spock.lang.Unroll

class RedisCommandSanitizerTest extends Specification {
  @Unroll
  def "should sanitize #expected"() {
    when:
    def sanitized = RedisCommandSanitizer.sanitize(command, args)

    then:
    sanitized == expected

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
    "RESTORE"          | ["key", "42", "value"]                                        | "RESTORE key 42 ?"
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
    // Server
    "CONFIG"           | ["SET", "masterpassword", "password"]                         | "CONFIG SET masterpassword ?"
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

  @Unroll
  def "should keep all arguments of #command"() {
    given:
    def args = ["arg1", "arg 2"]

    when:
    def sanitized = RedisCommandSanitizer.sanitize(command, args)

    then:
    sanitized == command + " " + args.join(" ")

    where:
    command << [
      // Cluster
      "CLUSTER", "READONLY", "READWRITE",
      // Connection
      "CLIENT", "ECHO", "PING", "QUIT", "SELECT",
      // Geo
      "GEOADD", "GEODIST", "GEOHASH", "GEOPOS", "GEORADIUS", "GEORADIUSBYMEMBER",
      // Hashes
      "HDEL", "HEXISTS", "HGET", "HGETALL", "HINCRBY", "HINCRBYFLOAT", "HKEYS", "HLEN", "HMGET",
      "HSCAN", "HSTRLEN", "HVALS",
      // HyperLogLog
      "PFCOUNT", "PFMERGE",
      // Keys
      "DEL", "DUMP", "EXISTS", "EXPIRE", "EXPIREAT", "KEYS", "MOVE", "OBJECT", "PERSIST", "PEXPIRE",
      "PEXPIREAT", "PTTL", "RANDOMKEY", "RENAME", "RENAMENX", "RESTORE", "SCAN", "SORT", "TOUCH",
      "TTL", "TYPE", "UNLINK", "WAIT",
      // Lists
      "BLMOVE", "BLPOP", "BRPOP", "BRPOPLPUSH", "LINDEX", "LLEN", "LMOVE", "LPOP", "LRANGE",
      "LTRIM", "RPOP", "RPOPLPUSH",
      // Pub/Sub
      "PSUBSCRIBE", "PUBSUB", "PUNSUBSCRIBE", "SUBSCRIBE", "UNSUBSCRIBE",
      // Server
      "ACL", "BGREWRITEAOF", "BGSAVE", "COMMAND", "DBSIZE", "DEBUG", "FLUSHALL", "FLUSHDB", "INFO",
      "LASTSAVE", "LATENCY", "LOLWUT", "MEMORY", "MODULE", "MONITOR", "PSYNC", "REPLICAOF", "ROLE",
      "SAVE", "SHUTDOWN", "SLAVEOF", "SLOWLOG", "SWAPDB", "SYNC", "TIME",
      // Sets
      "SCARD", "SDIFF", "SDIFFSTORE", "SINTER", "SINTERSTORE", "SMEMBERS", "SPOP", "SRANDMEMBER",
      "SSCAN", "SUNION", "SUNIONSTORE",
      // Sorted Sets
      "BZPOPMAX", "BZPOPMIN", "ZCARD", "ZINTER", "ZINTERSTORE", "ZPOPMAX", "ZPOPMIN", "ZRANGE",
      "ZREMRANGEBYRANK", "ZREVRANGE", "ZSCAN", "ZUNION", "ZUNIONSTORE",
      // Streams
      "XACK", "XCLAIM", "XDEL", "XGROUP", "XINFO", "XLEN", "XPENDING", "XRANGE", "XREAD",
      "XREADGROUP", "XREVRANGE", "XTRIM",
      // Strings
      "BITCOUNT", "BITFIELD", "BITOP", "BITPOS", "DECR", "DECRBY", "GET", "GETBIT", "GETRANGE",
      "INCR", "INCRBY", "INCRBYFLOAT", "MGET", "SETBIT", "STRALGO", "STRLEN",
      // Transactions
      "DISCARD", "EXEC", "MULTI", "UNWATCH", "WATCH"
    ]
  }

  def "should mask all arguments of an unknown command"() {
    when:
    def sanitized = RedisCommandSanitizer.sanitize("NEWAUTH", ["password", "secret"])

    then:
    sanitized == "NEWAUTH ? ?"
  }
}
