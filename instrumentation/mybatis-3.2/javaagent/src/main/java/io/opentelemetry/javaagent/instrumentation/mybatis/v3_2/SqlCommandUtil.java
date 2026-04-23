/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis.v3_2;

import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import org.apache.ibatis.binding.MapperMethod.SqlCommand;

public class SqlCommandUtil {
  private static final VirtualField<SqlCommand, ClassAndMethod> field =
      VirtualField.find(SqlCommand.class, ClassAndMethod.class);

  public static void setClassAndMethod(SqlCommand command, Class<?> clazz, Method method) {
    field.set(command, ClassAndMethod.create(clazz, method.getName()));
  }

  @Nullable
  public static ClassAndMethod getClassAndMethod(SqlCommand command) {
    return field.get(command);
  }

  private SqlCommandUtil() {}
}
