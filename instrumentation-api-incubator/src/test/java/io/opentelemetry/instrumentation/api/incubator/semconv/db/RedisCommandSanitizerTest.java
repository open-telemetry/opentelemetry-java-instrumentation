/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RedisCommandSanitizerTest {

  @ParameterizedTest
  @MethodSource("sanitizeArgs")
  void shouldSanitizeExpected(String command, List<String> args, String expected) {
    String result = RedisCommandSanitizer.create(true).sanitize(command, args);
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("keepAllArgumentsCommands")
  void shouldKeepAllArguments(String command) {
    List<String> args = list("arg1", "arg 2");
    String result = RedisCommandSanitizer.create(true).sanitize(command, args);
    assertThat(result).isEqualTo(command + " " + String.join(" ", args));
  }

  static Stream<Arguments> keepAllArgumentsCommands() {
    return Stream.of(
        // Cluster
        Arguments.of("CLUSTER"),
        Arguments.of("READONLY"),
        Arguments.of("READWRITE"),
        // Connection
        Arguments.of("CLIENT"),
        Arguments.of("ECHO"),
        Arguments.of("PING"),
        Arguments.of("QUIT"),
        Arguments.of("SELECT"),
        // Geo
        Arguments.of("GEOADD"),
        Arguments.of("GEODIST"),
        Arguments.of("GEOHASH"),
        Arguments.of("GEOPOS"),
        Arguments.of("GEORADIUS"),
        Arguments.of("GEORADIUSBYMEMBER"),
        // Hashes
        Arguments.of("HDEL"),
        Arguments.of("HEXISTS"),
        Arguments.of("HGET"),
        Arguments.of("HGETALL"),
        Arguments.of("HINCRBY"),
        Arguments.of("HINCRBYFLOAT"),
        Arguments.of("HKEYS"),
        Arguments.of("HLEN"),
        Arguments.of("HMGET"),
        Arguments.of("HSCAN"),
        Arguments.of("HSTRLEN"),
        Arguments.of("HVALS"),
        // HyperLogLog
        Arguments.of("PFCOUNT"),
        Arguments.of("PFMERGE"),
        // Keys
        Arguments.of("DEL"),
        Arguments.of("DUMP"),
        Arguments.of("EXISTS"),
        Arguments.of("EXPIRE"),
        Arguments.of("EXPIREAT"),
        Arguments.of("KEYS"),
        Arguments.of("MOVE"),
        Arguments.of("OBJECT"),
        Arguments.of("PERSIST"),
        Arguments.of("PEXPIRE"),
        Arguments.of("PEXPIREAT"),
        Arguments.of("PTTL"),
        Arguments.of("RANDOMKEY"),
        Arguments.of("RENAME"),
        Arguments.of("RENAMENX"),
        Arguments.of("RESTORE"),
        Arguments.of("SCAN"),
        Arguments.of("SORT"),
        Arguments.of("TOUCH"),
        Arguments.of("TTL"),
        Arguments.of("TYPE"),
        Arguments.of("UNLINK"),
        Arguments.of("WAIT"),
        // Lists
        Arguments.of("BLMOVE"),
        Arguments.of("BLPOP"),
        Arguments.of("BRPOP"),
        Arguments.of("BRPOPLPUSH"),
        Arguments.of("LINDEX"),
        Arguments.of("LLEN"),
        Arguments.of("LMOVE"),
        Arguments.of("LPOP"),
        Arguments.of("LRANGE"),
        Arguments.of("LTRIM"),
        Arguments.of("RPOP"),
        Arguments.of("RPOPLPUSH"),
        // Pub/Sub
        Arguments.of("PSUBSCRIBE"),
        Arguments.of("PUBSUB"),
        Arguments.of("PUNSUBSCRIBE"),
        Arguments.of("SUBSCRIBE"),
        Arguments.of("UNSUBSCRIBE"),
        // Server
        Arguments.of("ACL"),
        Arguments.of("BGREWRITEAOF"),
        Arguments.of("BGSAVE"),
        Arguments.of("COMMAND"),
        Arguments.of("DBSIZE"),
        Arguments.of("DEBUG"),
        Arguments.of("FLUSHALL"),
        Arguments.of("FLUSHDB"),
        Arguments.of("INFO"),
        Arguments.of("LASTSAVE"),
        Arguments.of("LATENCY"),
        Arguments.of("LOLWUT"),
        Arguments.of("MEMORY"),
        Arguments.of("MODULE"),
        Arguments.of("MONITOR"),
        Arguments.of("PSYNC"),
        Arguments.of("REPLICAOF"),
        Arguments.of("ROLE"),
        Arguments.of("SAVE"),
        Arguments.of("SHUTDOWN"),
        Arguments.of("SLAVEOF"),
        Arguments.of("SLOWLOG"),
        Arguments.of("SWAPDB"),
        Arguments.of("SYNC"),
        Arguments.of("TIME"),
        // Sets
        Arguments.of("SCARD"),
        Arguments.of("SDIFF"),
        Arguments.of("SDIFFSTORE"),
        Arguments.of("SINTER"),
        Arguments.of("SINTERSTORE"),
        Arguments.of("SMEMBERS"),
        Arguments.of("SPOP"),
        Arguments.of("SRANDMEMBER"),
        Arguments.of("SSCAN"),
        Arguments.of("SUNION"),
        Arguments.of("SUNIONSTORE"),
        // Sorted Sets
        Arguments.of("BZPOPMAX"),
        Arguments.of("BZPOPMIN"),
        Arguments.of("ZCARD"),
        Arguments.of("ZINTER"),
        Arguments.of("ZINTERSTORE"),
        Arguments.of("ZPOPMAX"),
        Arguments.of("ZPOPMIN"),
        Arguments.of("ZRANGE"),
        Arguments.of("ZREMRANGEBYRANK"),
        Arguments.of("ZREVRANGE"),
        Arguments.of("ZSCAN"),
        Arguments.of("ZUNION"),
        Arguments.of("ZUNIONSTORE"),
        // Streams
        Arguments.of("XACK"),
        Arguments.of("XCLAIM"),
        Arguments.of("XDEL"),
        Arguments.of("XGROUP"),
        Arguments.of("XINFO"),
        Arguments.of("XLEN"),
        Arguments.of("XPENDING"),
        Arguments.of("XRANGE"),
        Arguments.of("XREAD"),
        Arguments.of("XREADGROUP"),
        Arguments.of("XREVRANGE"),
        Arguments.of("XTRIM"),
        // Strings
        Arguments.of("BITCOUNT"),
        Arguments.of("BITFIELD"),
        Arguments.of("BITOP"),
        Arguments.of("BITPOS"),
        Arguments.of("DECR"),
        Arguments.of("DECRBY"),
        Arguments.of("GET"),
        Arguments.of("GETBIT"),
        Arguments.of("GETRANGE"),
        Arguments.of("INCR"),
        Arguments.of("INCRBY"),
        Arguments.of("INCRBYFLOAT"),
        Arguments.of("MGET"),
        Arguments.of("SETBIT"),
        Arguments.of("STRALGO"),
        Arguments.of("STRLEN"),
        // Transactions
        Arguments.of("DISCARD"),
        Arguments.of("EXEC"),
        Arguments.of("MULTI"),
        Arguments.of("UNWATCH"),
        Arguments.of("WATCH"));
  }

  @Test
  void maskAllArgsOfUnknownCommand() {
    String result =
        RedisCommandSanitizer.create(true).sanitize("NEWAUTH", list("password", "secret"));
    assertThat(result).isEqualTo("NEWAUTH ? ?");
  }

  static Stream<Arguments> sanitizeArgs() {
    return Stream.of(
        // Connection
        Arguments.of("AUTH", list("password"), "AUTH ?"),
        Arguments.of("HELLO", list("3", "AUTH", "username", "password"), "HELLO 3 AUTH ? ?"),
        Arguments.of("HELLO", list("3"), "HELLO 3"),
        // Hashes
        Arguments.of(
            "HMSET", list("hash", "key1", "value1", "key2", "value2"), "HMSET hash key1 ? key2 ?"),
        Arguments.of(
            "HSET", list("hash", "key1", "value1", "key2", "value2"), "HSET hash key1 ? key2 ?"),
        Arguments.of("HSETNX", list("hash", "key", "value"), "HSETNX hash key ?"),
        // HyperLogLog
        Arguments.of("PFADD", list("hll", "a", "b", "c"), "PFADD hll ? ? ?"),
        // Keys
        Arguments.of(
            "MIGRATE",
            list("127.0.0.1", "4242", "key", "0", "5000", "AUTH", "password"),
            "MIGRATE 127.0.0.1 4242 key 0 5000 AUTH ?"),
        Arguments.of("RESTORE", list("key", "42", "value"), "RESTORE key 42 ?"),
        // Lists
        Arguments.of(
            "LINSERT", list("list", "BEFORE", "value1", "value2"), "LINSERT list BEFORE ? ?"),
        Arguments.of("LPOS", list("list", "value"), "LPOS list ?"),
        Arguments.of("LPUSH", list("list", "value1", "value2"), "LPUSH list ? ?"),
        Arguments.of("LPUSHX", list("list", "value1", "value2"), "LPUSHX list ? ?"),
        Arguments.of("LREM", list("list", "2", "value"), "LREM list ? ?"),
        Arguments.of("LSET", list("list", "2", "value"), "LSET list ? ?"),
        Arguments.of("RPUSH", list("list", "value1", "value2"), "RPUSH list ? ?"),
        Arguments.of("RPUSHX", list("list", "value1", "value2"), "RPUSHX list ? ?"),
        // Pub/Sub
        Arguments.of("PUBLISH", list("channel", "message"), "PUBLISH channel ?"),
        // Scripting
        Arguments.of(
            "EVAL", list("script", "2", "key1", "key2", "value"), "EVAL script 2 key1 key2 ?"),
        Arguments.of("EVALSHA", list("sha1", "0", "value1", "value2"), "EVALSHA sha1 0 ? ?"),
        // Sets),
        Arguments.of("SADD", list("set", "value1", "value2"), "SADD set ? ?"),
        Arguments.of("SISMEMBER", list("set", "value"), "SISMEMBER set ?"),
        Arguments.of("SMISMEMBER", list("set", "value1", "value2"), "SMISMEMBER set ? ?"),
        Arguments.of("SMOVE", list("set1", "set2", "value"), "SMOVE set1 set2 ?"),
        Arguments.of("SREM", list("set", "value1", "value2"), "SREM set ? ?"),
        // Server
        Arguments.of(
            "CONFIG", list("SET", "masterpassword", "password"), "CONFIG SET masterpassword ?"),
        // Sorted Sets
        Arguments.of("ZADD", list("sset", "1", "value1", "2", "value2"), "ZADD sset ? ? ? ?"),
        Arguments.of("ZCOUNT", list("sset", "1", "10"), "ZCOUNT sset ? ?"),
        Arguments.of("ZINCRBY", list("sset", "1", "value"), "ZINCRBY sset ? ?"),
        Arguments.of("ZLEXCOUNT", list("sset", "1", "10"), "ZLEXCOUNT sset ? ?"),
        Arguments.of("ZMSCORE", list("sset", "value1", "value2"), "ZMSCORE sset ? ?"),
        Arguments.of("ZRANGEBYLEX", list("sset", "1", "10"), "ZRANGEBYLEX sset ? ?"),
        Arguments.of("ZRANGEBYSCORE", list("sset", "1", "10"), "ZRANGEBYSCORE sset ? ?"),
        Arguments.of("ZRANK", list("sset", "value"), "ZRANK sset ?"),
        Arguments.of("ZREM", list("sset", "value1", "value2"), "ZREM sset ? ?"),
        Arguments.of("ZREMRANGEBYLEX", list("sset", "1", "10"), "ZREMRANGEBYLEX sset ? ?"),
        Arguments.of("ZREMRANGEBYSCORE", list("sset", "1", "10"), "ZREMRANGEBYSCORE sset ? ?"),
        Arguments.of("ZREVRANGEBYLEX", list("sset", "1", "10"), "ZREVRANGEBYLEX sset ? ?"),
        Arguments.of("ZREVRANGEBYSCORE", list("sset", "1", "10"), "ZREVRANGEBYSCORE sset ? ?"),
        Arguments.of("ZREVRANK", list("sset", "value"), "ZREVRANK sset ?"),
        Arguments.of("ZSCORE", list("sset", "value"), "ZSCORE sset ?"),
        // Streams
        Arguments.of(
            "XADD",
            list("stream", "*", "key1", "value1", "key2", "value2"),
            "XADD stream * key1 ? key2 ?"),
        // Strings
        Arguments.of("APPEND", list("key", "value"), "APPEND key ?"),
        Arguments.of("GETSET", list("key", "value"), "GETSET key ?"),
        Arguments.of("MSET", list("key1", "value1", "key2", "value2"), "MSET key1 ? key2 ?"),
        Arguments.of("MSETNX", list("key1", "value1", "key2", "value2"), "MSETNX key1 ? key2 ?"),
        Arguments.of("PSETEX", list("key", "10000", "value"), "PSETEX key 10000 ?"),
        Arguments.of("SET", list("key", "value"), "SET key ?"),
        Arguments.of("SETEX", list("key", "10", "value"), "SETEX key 10 ?"),
        Arguments.of("SETNX", list("key", "value"), "SETNX key ?"),
        Arguments.of("SETRANGE", list("key", "42", "value"), "SETRANGE key ? ?"));
  }

  static List<String> list(String... args) {
    return Arrays.asList(args);
  }
}
