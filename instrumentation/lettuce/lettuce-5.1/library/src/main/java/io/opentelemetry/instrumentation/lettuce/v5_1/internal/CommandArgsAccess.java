/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1.internal;

import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.protocol.CommandArgs;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Reflective accessor for the package-private {@code CommandArgs#singularArguments} field and its
 * package-private inner argument types.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class CommandArgsAccess {

  private static final Logger logger = Logger.getLogger(CommandArgsAccess.class.getName());

  @Nullable
  private static final Field singularArgumentsField =
      findField(CommandArgs.class, "singularArguments");

  @Nullable
  private static final Class<?> keyArgumentClass =
      loadNestedClass("io.lettuce.core.protocol.CommandArgs$KeyArgument");

  @Nullable
  private static final Class<?> valueArgumentClass =
      loadNestedClass("io.lettuce.core.protocol.CommandArgs$ValueArgument");

  @Nullable private static final Field keyArgumentCodecField = findField(keyArgumentClass, "codec");
  @Nullable private static final Field keyArgumentKeyField = findField(keyArgumentClass, "key");

  @Nullable
  private static final Field valueArgumentCodecField = findField(valueArgumentClass, "codec");

  @Nullable
  private static final Field valueArgumentValField = findField(valueArgumentClass, "val");

  @Nullable
  private static Class<?> loadNestedClass(String name) {
    try {
      return Class.forName(name, false, CommandArgs.class.getClassLoader());
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Failed to load class " + name, t);
      return null;
    }
  }

  @Nullable
  private static Field findField(@Nullable Class<?> owner, String name) {
    if (owner == null) {
      return null;
    }
    try {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      return field;
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Failed to locate " + owner.getName() + "#" + name + " field", t);
      return null;
    }
  }

  public static List<String> getCommandArgs(CommandArgs<?, ?> commandArgs) {
    if (singularArgumentsField == null) {
      return Collections.emptyList();
    }
    List<?> singularArguments;
    try {
      singularArguments = (List<?>) singularArgumentsField.get(commandArgs);
    } catch (Throwable t) {
      logger.log(Level.FINE, "Failed to read CommandArgs#singularArguments field", t);
      return Collections.emptyList();
    }
    if (singularArguments == null) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>(singularArguments.size());
    for (Object argument : singularArguments) {
      if (argument == null) {
        continue;
      }
      result.add(getArgValue(StringCodec.UTF8, argument));
    }
    return result;
  }

  // casts are guarded by explicit isInstance checks against the reflectively looked-up nested types
  @SuppressWarnings("unchecked")
  private static String getArgValue(StringCodec stringCodec, Object argument) {
    try {
      if (keyArgumentClass != null
          && keyArgumentClass.isInstance(argument)
          && keyArgumentCodecField != null
          && keyArgumentKeyField != null) {
        RedisCodec<Object, ?> codec = (RedisCodec<Object, ?>) keyArgumentCodecField.get(argument);
        Object key = keyArgumentKeyField.get(argument);
        ByteBuffer encoded = codec.encodeKey(key);
        return stringCodec.decodeKey(encoded);
      }
      if (valueArgumentClass != null
          && valueArgumentClass.isInstance(argument)
          && valueArgumentCodecField != null
          && valueArgumentValField != null) {
        RedisCodec<?, Object> codec =
            (RedisCodec<?, Object>) valueArgumentCodecField.get(argument);
        Object val = valueArgumentValField.get(argument);
        ByteBuffer encoded = codec.encodeValue(val);
        return stringCodec.decodeValue(encoded);
      }
    } catch (Throwable t) {
      logger.log(Level.FINE, "Failed to reflectively decode CommandArgs argument", t);
    }
    return argument.toString();
  }

  private CommandArgsAccess() {}
}
