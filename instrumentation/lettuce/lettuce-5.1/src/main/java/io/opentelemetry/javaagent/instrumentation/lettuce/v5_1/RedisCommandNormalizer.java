/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static java.util.Collections.unmodifiableMap;

import io.opentelemetry.javaagent.instrumentation.lettuce.v5_1.RedisCommandNormalizer.CommandNormalizer.CommandAndNumArgs;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_1.RedisCommandNormalizer.CommandNormalizer.Default;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_1.RedisCommandNormalizer.CommandNormalizer.Eval;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_1.RedisCommandNormalizer.CommandNormalizer.MultiKeyValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** This class is responsible for masking potentially sensitive data in Redis commands. */
public final class RedisCommandNormalizer {

  private static final Map<String, CommandNormalizer> NORMALIZERS;

  static {
    Map<String, CommandNormalizer> normalizers = new HashMap<>();

    CommandNormalizer keepOneArg = new CommandAndNumArgs(1);
    CommandNormalizer keepTwoArgs = new CommandAndNumArgs(2);
    CommandNormalizer setMultiHashField = new MultiKeyValue(1);
    CommandNormalizer setMultiField = new MultiKeyValue(0);

    // Connection
    // can contain AUTH data
    normalizers.put("AUTH", new CommandAndNumArgs(0));
    normalizers.put("HELLO", keepTwoArgs);

    // Hashes
    normalizers.put("HMSET", setMultiHashField);
    normalizers.put("HSET", setMultiHashField);
    normalizers.put("HSETNX", keepTwoArgs);

    // HyperLogLog
    normalizers.put("PFADD", keepOneArg);

    // Keys
    // can contain AUTH data
    normalizers.put("MIGRATE", new CommandAndNumArgs(6));

    // Lists
    normalizers.put("LINSERT", keepTwoArgs);
    normalizers.put("LPOS", keepOneArg);
    normalizers.put("LPUSH", keepOneArg);
    normalizers.put("LPUSHX", keepOneArg);
    normalizers.put("LREM", keepOneArg);
    normalizers.put("LSET", keepOneArg);
    normalizers.put("RPUSH", keepOneArg);
    normalizers.put("RPUSHX", keepOneArg);

    // Pub/Sub
    normalizers.put("PUBLISH", keepOneArg);

    // Scripting
    normalizers.put("EVAL", Eval.INSTANCE);
    normalizers.put("EVALSHA", Eval.INSTANCE);

    // Sets
    normalizers.put("SADD", keepOneArg);
    normalizers.put("SISMEMBER", keepOneArg);
    normalizers.put("SMISMEMBER", keepOneArg);
    normalizers.put("SMOVE", keepTwoArgs);
    normalizers.put("SREM", keepOneArg);

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

    // Streams
    normalizers.put("XADD", new MultiKeyValue(2));

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

    NORMALIZERS = unmodifiableMap(normalizers);
  }

  public static String normalize(String command, List<String> args) {
    return NORMALIZERS
        .getOrDefault(command.toUpperCase(), Default.INSTANCE)
        .normalize(command, args);
  }

  public interface CommandNormalizer {
    String normalize(String command, List<String> args);

    enum Default implements CommandNormalizer {
      INSTANCE;

      @Override
      public String normalize(String command, List<String> args) {
        StringBuilder normalised = new StringBuilder(command);
        for (String arg : args) {
          normalised.append(" ").append(arg);
        }
        return normalised.toString();
      }
    }

    class CommandAndNumArgs implements CommandNormalizer {
      private final int numOfArgsToKeep;

      public CommandAndNumArgs(int numOfArgsToKeep) {
        this.numOfArgsToKeep = numOfArgsToKeep;
      }

      @Override
      public String normalize(String command, List<String> args) {
        StringBuilder normalised = new StringBuilder(command);
        int i = 0;
        for (; i < numOfArgsToKeep && i < args.size(); ++i) {
          normalised.append(" ").append(args.get(i));
        }
        for (; i < args.size(); ++i) {
          normalised.append(" ").append("?");
        }
        return normalised.toString();
      }
    }

    class MultiKeyValue implements CommandNormalizer {
      private final int numOfArgsBeforeKeyValue;

      public MultiKeyValue(int numOfArgsBeforeKeyValue) {
        this.numOfArgsBeforeKeyValue = numOfArgsBeforeKeyValue;
      }

      @Override
      public String normalize(String command, List<String> args) {
        StringBuilder normalised = new StringBuilder(command);
        int i = 0;
        // append all "initial" arguments before key-value pairs start
        for (; i < numOfArgsBeforeKeyValue && i < args.size(); ++i) {
          normalised.append(" ").append(args.get(i));
        }

        // whether keys are on even or odd index depends on the number of initial args
        int keys = numOfArgsBeforeKeyValue % 2;
        for (; i < args.size(); ++i) {
          normalised
              .append(" ")
              // append only keys, skip values
              .append(i % 2 == keys ? args.get(i) : "?");
        }
        return normalised.toString();
      }
    }

    enum Eval implements CommandNormalizer {
      INSTANCE;

      @Override
      public String normalize(String command, List<String> args) {
        StringBuilder normalised = new StringBuilder(command);

        // get the number of keys passed from the command itself (second arg)
        int numberOfKeys = 0;
        if (args.size() > 2) {
          try {
            numberOfKeys = Integer.parseInt(args.get(1));
          } catch (NumberFormatException ignored) {
          }
        }

        int i = 0;
        // log the script, number of keys and all keys
        for (; i < (numberOfKeys + 2) && i < args.size(); ++i) {
          normalised.append(" ").append(args.get(i));
        }
        // mask the rest
        for (; i < args.size(); ++i) {
          normalised.append(" ").append("?");
        }
        return normalised.toString();
      }
    }
  }

  private RedisCommandNormalizer() {}
}
