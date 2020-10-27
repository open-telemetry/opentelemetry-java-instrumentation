/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.db;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableMap;

import io.opentelemetry.javaagent.instrumentation.api.db.RedisCommandNormalizer.CommandNormalizer.CommandAndNumArgs;
import io.opentelemetry.javaagent.instrumentation.api.db.RedisCommandNormalizer.CommandNormalizer.Eval;
import io.opentelemetry.javaagent.instrumentation.api.db.RedisCommandNormalizer.CommandNormalizer.KeepAllArgs;
import io.opentelemetry.javaagent.instrumentation.api.db.RedisCommandNormalizer.CommandNormalizer.MultiKeyValue;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for masking potentially sensitive data in Redis commands.
 *
 * <p>Examples:
 *
 * <table>
 *   <tr>
 *     <th>Raw command</th>
 *     <th>Normalized command</th>
 *   </tr>
 *   <tr>
 *     <td>{@code AUTH password}</td>
 *     <td>{@code AUTH ?}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code HMSET hash creditcard 1234567887654321 address asdf}</td>
 *     <td>{@code HMSET hash creditcard ? address ?}</td>
 *   </tr>
 * </table>
 */
public final class RedisCommandNormalizer {

  private static final Map<String, CommandNormalizer> NORMALIZERS;
  private static final CommandNormalizer DEFAULT = new CommandAndNumArgs(0);

  static {
    Map<String, CommandNormalizer> normalizers = new HashMap<>();

    CommandNormalizer keepOneArg = new CommandAndNumArgs(1);
    CommandNormalizer keepTwoArgs = new CommandAndNumArgs(2);
    CommandNormalizer setMultiHashField = new MultiKeyValue(1);
    CommandNormalizer setMultiField = new MultiKeyValue(0);

    // Cluster
    for (String command : asList("CLUSTER", "READONLY", "READWRITE")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Connection
    normalizers.put("AUTH", DEFAULT);
    // HELLO can contain AUTH data
    normalizers.put("HELLO", keepTwoArgs);
    for (String command : asList("CLIENT", "ECHO", "PING", "QUIT", "SELECT")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Geo
    for (String command :
        asList("GEOADD", "GEODIST", "GEOHASH", "GEOPOS", "GEORADIUS", "GEORADIUSBYMEMBER")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Hashes
    normalizers.put("HMSET", setMultiHashField);
    normalizers.put("HSET", setMultiHashField);
    normalizers.put("HSETNX", keepTwoArgs);
    for (String command :
        asList(
            "HDEL",
            "HEXISTS",
            "HGET",
            "HGETALL",
            "HINCRBY",
            "HINCRBYFLOAT",
            "HKEYS",
            "HLEN",
            "HMGET",
            "HSCAN",
            "HSTRLEN",
            "HVALS")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // HyperLogLog
    normalizers.put("PFADD", keepOneArg);
    for (String command : asList("PFCOUNT", "PFMERGE")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Keys
    // MIGRATE can contain AUTH data
    normalizers.put("MIGRATE", new CommandAndNumArgs(6));
    normalizers.put("RESTORE", keepTwoArgs);
    for (String command :
        asList(
            "DEL",
            "DUMP",
            "EXISTS",
            "EXPIRE",
            "EXPIREAT",
            "KEYS",
            "MOVE",
            "OBJECT",
            "PERSIST",
            "PEXPIRE",
            "PEXPIREAT",
            "PTTL",
            "RANDOMKEY",
            "RENAME",
            "RENAMENX",
            "SCAN",
            "SORT",
            "TOUCH",
            "TTL",
            "TYPE",
            "UNLINK",
            "WAIT")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Lists
    normalizers.put("LINSERT", keepTwoArgs);
    normalizers.put("LPOS", keepOneArg);
    normalizers.put("LPUSH", keepOneArg);
    normalizers.put("LPUSHX", keepOneArg);
    normalizers.put("LREM", keepOneArg);
    normalizers.put("LSET", keepOneArg);
    normalizers.put("RPUSH", keepOneArg);
    normalizers.put("RPUSHX", keepOneArg);
    for (String command :
        asList(
            "BLMOVE",
            "BLPOP",
            "BRPOP",
            "BRPOPLPUSH",
            "LINDEX",
            "LLEN",
            "LMOVE",
            "LPOP",
            "LRANGE",
            "LTRIM",
            "RPOP",
            "RPOPLPUSH")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Pub/Sub
    normalizers.put("PUBLISH", keepOneArg);
    for (String command :
        asList("PSUBSCRIBE", "PUBSUB", "PUNSUBSCRIBE", "SUBSCRIBE", "UNSUBSCRIBE")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Scripting
    normalizers.put("EVAL", Eval.INSTANCE);
    normalizers.put("EVALSHA", Eval.INSTANCE);
    normalizers.put("SCRIPT", KeepAllArgs.INSTANCE);

    // Server
    // CONFIG SET can set any property, including the master password
    normalizers.put("CONFIG", keepTwoArgs);
    for (String command :
        asList(
            "ACL",
            "BGREWRITEAOF",
            "BGSAVE",
            "COMMAND",
            "DBSIZE",
            "DEBUG",
            "FLUSHALL",
            "FLUSHDB",
            "INFO",
            "LASTSAVE",
            "LATENCY",
            "LOLWUT",
            "MEMORY",
            "MODULE",
            "MONITOR",
            "PSYNC",
            "REPLICAOF",
            "ROLE",
            "SAVE",
            "SHUTDOWN",
            "SLAVEOF",
            "SLOWLOG",
            "SWAPDB",
            "SYNC",
            "TIME")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Sets
    normalizers.put("SADD", keepOneArg);
    normalizers.put("SISMEMBER", keepOneArg);
    normalizers.put("SMISMEMBER", keepOneArg);
    normalizers.put("SMOVE", keepTwoArgs);
    normalizers.put("SREM", keepOneArg);
    for (String command :
        asList(
            "SCARD",
            "SDIFF",
            "SDIFFSTORE",
            "SINTER",
            "SINTERSTORE",
            "SMEMBERS",
            "SPOP",
            "SRANDMEMBER",
            "SSCAN",
            "SUNION",
            "SUNIONSTORE")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Sorted Sets
    normalizers.put("ZADD", keepOneArg);
    normalizers.put("ZCOUNT", keepOneArg);
    normalizers.put("ZINCRBY", keepOneArg);
    normalizers.put("ZLEXCOUNT", keepOneArg);
    normalizers.put("ZMSCORE", keepOneArg);
    normalizers.put("ZRANGEBYLEX", keepOneArg);
    normalizers.put("ZRANGEBYSCORE", keepOneArg);
    normalizers.put("ZRANK", keepOneArg);
    normalizers.put("ZREM", keepOneArg);
    normalizers.put("ZREMRANGEBYLEX", keepOneArg);
    normalizers.put("ZREMRANGEBYSCORE", keepOneArg);
    normalizers.put("ZREVRANGEBYLEX", keepOneArg);
    normalizers.put("ZREVRANGEBYSCORE", keepOneArg);
    normalizers.put("ZREVRANK", keepOneArg);
    normalizers.put("ZSCORE", keepOneArg);
    for (String command :
        asList(
            "BZPOPMAX",
            "BZPOPMIN",
            "ZCARD",
            "ZINTER",
            "ZINTERSTORE",
            "ZPOPMAX",
            "ZPOPMIN",
            "ZRANGE",
            "ZREMRANGEBYRANK",
            "ZREVRANGE",
            "ZSCAN",
            "ZUNION",
            "ZUNIONSTORE")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Streams
    normalizers.put("XADD", new MultiKeyValue(2));
    for (String command :
        asList(
            "XACK",
            "XCLAIM",
            "XDEL",
            "XGROUP",
            "XINFO",
            "XLEN",
            "XPENDING",
            "XRANGE",
            "XREAD",
            "XREADGROUP",
            "XREVRANGE",
            "XTRIM")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Strings
    normalizers.put("APPEND", keepOneArg);
    normalizers.put("GETSET", keepOneArg);
    normalizers.put("MSET", setMultiField);
    normalizers.put("MSETNX", setMultiField);
    normalizers.put("PSETEX", keepTwoArgs);
    normalizers.put("SET", keepOneArg);
    normalizers.put("SETEX", keepTwoArgs);
    normalizers.put("SETNX", keepOneArg);
    normalizers.put("SETRANGE", keepOneArg);
    for (String command :
        asList(
            "BITCOUNT",
            "BITFIELD",
            "BITOP",
            "BITPOS",
            "DECR",
            "DECRBY",
            "GET",
            "GETBIT",
            "GETRANGE",
            "INCR",
            "INCRBY",
            "INCRBYFLOAT",
            "MGET",
            "SETBIT",
            "STRALGO",
            "STRLEN")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Transactions
    for (String command : asList("DISCARD", "EXEC", "MULTI", "UNWATCH", "WATCH")) {
      normalizers.put(command, KeepAllArgs.INSTANCE);
    }

    NORMALIZERS = unmodifiableMap(normalizers);
  }

  public static String normalize(String command, List<?> args) {
    return NORMALIZERS.getOrDefault(command.toUpperCase(), DEFAULT).normalize(command, args);
  }

  public interface CommandNormalizer {
    String normalize(String command, List<?> args);

    static String argToString(Object arg) {
      if (arg instanceof byte[]) {
        return new String((byte[]) arg, StandardCharsets.UTF_8);
      } else {
        return arg.toString();
      }
    }

    enum KeepAllArgs implements CommandNormalizer {
      INSTANCE;

      @Override
      public String normalize(String command, List<?> args) {
        StringBuilder normalised = new StringBuilder(command);
        for (Object arg : args) {
          normalised.append(" ").append(argToString(arg));
        }
        return normalised.toString();
      }
    }

    // keeps only a chosen number of arguments
    // example for num=2: CMD arg1 arg2 ? ?
    class CommandAndNumArgs implements CommandNormalizer {
      private final int numOfArgsToKeep;

      public CommandAndNumArgs(int numOfArgsToKeep) {
        this.numOfArgsToKeep = numOfArgsToKeep;
      }

      @Override
      public String normalize(String command, List<?> args) {
        StringBuilder normalised = new StringBuilder(command);
        for (int i = 0; i < numOfArgsToKeep && i < args.size(); ++i) {
          normalised.append(" ").append(argToString(args.get(i)));
        }
        for (int i = numOfArgsToKeep; i < args.size(); ++i) {
          normalised.append(" ?");
        }
        return normalised.toString();
      }
    }

    // keeps only chosen number of arguments and then every second one
    // example for num=2: CMD arg1 arg2 key1 ? key2 ?
    class MultiKeyValue implements CommandNormalizer {
      private final int numOfArgsBeforeKeyValue;

      public MultiKeyValue(int numOfArgsBeforeKeyValue) {
        this.numOfArgsBeforeKeyValue = numOfArgsBeforeKeyValue;
      }

      @Override
      public String normalize(String command, List<?> args) {
        StringBuilder normalised = new StringBuilder(command);
        // append all "initial" arguments before key-value pairs start
        for (int i = 0; i < numOfArgsBeforeKeyValue && i < args.size(); ++i) {
          normalised.append(" ").append(argToString(args.get(i)));
        }

        // loop over keys only
        for (int i = numOfArgsBeforeKeyValue; i < args.size(); i += 2) {
          normalised.append(" ").append(argToString(args.get(i))).append(" ?");
        }
        return normalised.toString();
      }
    }

    enum Eval implements CommandNormalizer {
      INSTANCE;

      @Override
      public String normalize(String command, List<?> args) {
        StringBuilder normalised = new StringBuilder(command);

        // get the number of keys passed from the command itself (second arg)
        int numberOfKeys = 0;
        if (args.size() > 2) {
          try {
            numberOfKeys = Integer.parseInt(argToString(args.get(1)));
          } catch (NumberFormatException ignored) {
          }
        }

        int i = 0;
        // log the script, number of keys and all keys
        for (; i < (numberOfKeys + 2) && i < args.size(); ++i) {
          normalised.append(" ").append(argToString(args.get(i)));
        }
        // mask the rest
        for (; i < args.size(); ++i) {
          normalised.append(" ?");
        }
        return normalised.toString();
      }
    }
  }

  private RedisCommandNormalizer() {}
}
