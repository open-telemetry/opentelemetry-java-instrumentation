/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static org.assertj.core.api.Assertions.assertThat;

import codes.rafael.asmjdkbridge.JdkClassReader;
import codes.rafael.asmjdkbridge.JdkClassWriter;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilderImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;

class RealFieldInjectorTest {

  private static final String TYPE_NAME = Target.class.getName();
  private static final String FIELD_TYPE_NAME = Runnable.class.getName();
  private static final String FIELD_NAME = "test";

  @Test
  @EnabledForJreRange(min = JRE.JAVA_24)
  void writesFieldWhenUsingJdkClassFileApiWriter() throws IOException {
    VirtualFieldMappingsBuilderImpl mappingsBuilder = new VirtualFieldMappingsBuilderImpl();
    mappingsBuilder.register(TYPE_NAME, FIELD_TYPE_NAME, FIELD_NAME);
    RealFieldInjector injector =
        new RealFieldInjector(
            new FieldAccessorInterfacesGenerator(new ByteBuddy())
                .generateFieldAccessorInterfaces(mappingsBuilder.build()),
            TYPE_NAME,
            FIELD_TYPE_NAME,
            FIELD_NAME);
    JdkClassReader reader = new JdkClassReader(ClassFileLocator.ForClassLoader.read(Target.class));
    JdkClassWriter writer = new JdkClassWriter(reader, 0);

    reader.accept(
        injector.wrap(
            TypeDescription.ForLoadedType.of(Target.class), writer, null, null, null, null, 0, 0),
        0);

    assertThat(fieldNames(writer.toByteArray()))
        .containsExactly(
            GeneratedVirtualFieldNames.getRealFieldName(TYPE_NAME, FIELD_TYPE_NAME, FIELD_NAME));
  }

  private static List<String> fieldNames(byte[] classBytes) {
    List<String> names = new ArrayList<>();
    new ClassReader(classBytes)
        .accept(
            new ClassVisitor(AsmApi.VERSION) {
              @Override
              public FieldVisitor visitField(
                  int access, String name, String descriptor, String signature, Object value) {
                names.add(name);
                return null;
              }
            },
            0);
    return names;
  }

  static class Target {}
}
