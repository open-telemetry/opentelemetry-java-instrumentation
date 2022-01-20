/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableMap;

import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer.CommandSanitizer.CommandAndNumArgs;
import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer.CommandSanitizer.Eval;
import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer.CommandSanitizer.KeepAllArgs;
import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer.CommandSanitizer.MultiKeyValue;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
public final class RedisCommandSanitizer {

  private static final Map<String, CommandSanitizer> SANITIZERS;
  private static final CommandSanitizer DEFAULT = new CommandAndNumArgs(0);

  static {
    Map<String, CommandSanitizer> sanitizers = new HashMap<>();

    CommandSanitizer keepOneArg = new CommandAndNumArgs(1);
    CommandSanitizer keepTwoArgs = new CommandAndNumArgs(2);
    CommandSanitizer setMultiHashField = new MultiKeyValue(1);
    CommandSanitizer setMultiField = new MultiKeyValue(0);

    // Cluster
    for (String command : asList("CLUSTER", "READONLY", "READWRITE")) {
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Connection
    sanitizers.put("AUTH", DEFAULT);
    // HELLO can contain AUTH data
    sanitizers.put("HELLO", keepTwoArgs);
    for (String command : asList("CLIENT", "ECHO", "PING", "QUIT", "SELECT")) {
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Geo
    for (String command :
        asList(
            "GEOADD",
            "GEODIST",
            "GEOHASH",
            "GEOPOS",
            "GEORADIUS",
            "GEORADIUS_RO",
            "GEORADIUSBYMEMBER")) {
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Hashes
    sanitizers.put("HMSET", setMultiHashField);
    sanitizers.put("HSET", setMultiHashField);
    sanitizers.put("HSETNX", keepTwoArgs);
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
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // HyperLogLog
    sanitizers.put("PFADD", keepOneArg);
    for (String command : asList("PFCOUNT", "PFMERGE")) {
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Keys
    // MIGRATE can contain AUTH data
    sanitizers.put("MIGRATE", new CommandAndNumArgs(6));
    sanitizers.put("RESTORE", keepTwoArgs);
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
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Lists
    sanitizers.put("LINSERT", keepTwoArgs);
    sanitizers.put("LPOS", keepOneArg);
    sanitizers.put("LPUSH", keepOneArg);
    sanitizers.put("LPUSHX", keepOneArg);
    sanitizers.put("LREM", keepOneArg);
    sanitizers.put("LSET", keepOneArg);
    sanitizers.put("RPUSH", keepOneArg);
    sanitizers.put("RPUSHX", keepOneArg);
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
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Pub/Sub
    sanitizers.put("PUBLISH", keepOneArg);
    for (String command :
        asList("PSUBSCRIBE", "PUBSUB", "PUNSUBSCRIBE", "SUBSCRIBE", "UNSUBSCRIBE")) {
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Scripting
    sanitizers.put("EVAL", Eval.INSTANCE);
    sanitizers.put("EVALSHA", Eval.INSTANCE);
    sanitizers.put("SCRIPT", KeepAllArgs.INSTANCE);

    // Server
    // CONFIG SET can set any property, including the master password
    sanitizers.put("CONFIG", keepTwoArgs);
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
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Sets
    sanitizers.put("SADD", keepOneArg);
    sanitizers.put("SISMEMBER", keepOneArg);
    sanitizers.put("SMISMEMBER", keepOneArg);
    sanitizers.put("SMOVE", keepTwoArgs);
    sanitizers.put("SREM", keepOneArg);
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
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Sorted Sets
    sanitizers.put("ZADD", keepOneArg);
    sanitizers.put("ZCOUNT", keepOneArg);
    sanitizers.put("ZINCRBY", keepOneArg);
    sanitizers.put("ZLEXCOUNT", keepOneArg);
    sanitizers.put("ZMSCORE", keepOneArg);
    sanitizers.put("ZRANGEBYLEX", keepOneArg);
    sanitizers.put("ZRANGEBYSCORE", keepOneArg);
    sanitizers.put("ZRANK", keepOneArg);
    sanitizers.put("ZREM", keepOneArg);
    sanitizers.put("ZREMRANGEBYLEX", keepOneArg);
    sanitizers.put("ZREMRANGEBYSCORE", keepOneArg);
    sanitizers.put("ZREVRANGEBYLEX", keepOneArg);
    sanitizers.put("ZREVRANGEBYSCORE", keepOneArg);
    sanitizers.put("ZREVRANK", keepOneArg);
    sanitizers.put("ZSCORE", keepOneArg);
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
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Streams
    sanitizers.put("XADD", new MultiKeyValue(2));
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
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Strings
    sanitizers.put("APPEND", keepOneArg);
    sanitizers.put("GETSET", keepOneArg);
    sanitizers.put("MSET", setMultiField);
    sanitizers.put("MSETNX", setMultiField);
    sanitizers.put("PSETEX", keepTwoArgs);
    sanitizers.put("SET", keepOneArg);
    sanitizers.put("SETEX", keepTwoArgs);
    sanitizers.put("SETNX", keepOneArg);
    sanitizers.put("SETRANGE", keepOneArg);
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
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    // Transactions
    for (String command : asList("DISCARD", "EXEC", "MULTI", "UNWATCH", "WATCH")) {
      sanitizers.put(command, KeepAllArgs.INSTANCE);
    }

    SANITIZERS = unmodifiableMap(sanitizers);
  }

  public static String sanitize(String command, List<?> args) {
    if (!StatementSanitizationConfig.isStatementSanitizationEnabled()) {
      return KeepAllArgs.INSTANCE.sanitize(command, args);
    }
    return SANITIZERS
        .getOrDefault(command.toUpperCase(Locale.ROOT), DEFAULT)
        .sanitize(command, args);
  }

  public interface CommandSanitizer {
    String sanitize(String command, List<?> args);

    static String argToString(Object arg) {
      if (arg instanceof byte[]) {
        return new String((byte[]) arg, StandardCharsets.UTF_8);
      } else {
        return arg.toString();
      }
    }

    enum KeepAllArgs implements CommandSanitizer {
      INSTANCE;

      @Override
      public String sanitize(String command, List<?> args) {
        StringBuilder sanitized = new StringBuilder(command);
        for (Object arg : args) {
          sanitized.append(" ").append(argToString(arg));
        }
        return sanitized.toString();
      }
    }

    // keeps only a chosen number of arguments
    // example for num=2: CMD arg1 arg2 ? ?
    class CommandAndNumArgs implements CommandSanitizer {
      private final int numOfArgsToKeep;

      public CommandAndNumArgs(int numOfArgsToKeep) {
        this.numOfArgsToKeep = numOfArgsToKeep;
      }

      @Override
      public String sanitize(String command, List<?> args) {
        StringBuilder sanitized = new StringBuilder(command);
        for (int i = 0; i < numOfArgsToKeep && i < args.size(); ++i) {
          sanitized.append(" ").append(argToString(args.get(i)));
        }
        for (int i = numOfArgsToKeep; i < args.size(); ++i) {
          sanitized.append(" ?");
        }
        return sanitized.toString();
      }
    }

    // keeps only chosen number of arguments and then every second one
    // example for num=2: CMD arg1 arg2 key1 ? key2 ?
    class MultiKeyValue implements CommandSanitizer {
      private final int numOfArgsBeforeKeyValue;

      public MultiKeyValue(int numOfArgsBeforeKeyValue) {
        this.numOfArgsBeforeKeyValue = numOfArgsBeforeKeyValue;
      }

      @Override
      public String sanitize(String command, List<?> args) {
        StringBuilder sanitized = new StringBuilder(command);
        // append all "initial" arguments before key-value pairs start
        for (int i = 0; i < numOfArgsBeforeKeyValue && i < args.size(); ++i) {
          sanitized.append(" ").append(argToString(args.get(i)));
        }

        // loop over keys only
        for (int i = numOfArgsBeforeKeyValue; i < args.size(); i += 2) {
          sanitized.append(" ").append(argToString(args.get(i))).append(" ?");
        }
        return sanitized.toString();
      }
    }

    enum Eval implements CommandSanitizer {
      INSTANCE;

      @Override
      public String sanitize(String command, List<?> args) {
        StringBuilder sanitized = new StringBuilder(command);

        // get the number of keys passed from the command itself (second arg)
        int numberOfKeys = 0;
        if (args.size() > 2) {
          try {
            numberOfKeys = Integer.parseInt(argToString(args.get(1)));
          } catch (NumberFormatException ignored) {
            // Ignore
          }
        }

        int i = 0;
        // log the script, number of keys and all keys
        for (; i < (numberOfKeys + 2) && i < args.size(); ++i) {
          sanitized.append(" ").append(argToString(args.get(i)));
        }
        // mask the rest
        for (; i < args.size(); ++i) {
          sanitized.append(" ?");
        }
        return sanitized.toString();
      }
    }
  }

  private RedisCommandSanitizer() {}
}
