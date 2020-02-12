package io.opentelemetry.auto.tooling;

import static io.opentelemetry.auto.tooling.ShadingRemapper.rule;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.commons.ClassRemapper;

public class ExporterClassLoader extends URLClassLoader {
  // We need to prefix the names to prevent the shadowJar relocation rules from touching them.
  private final ShadingRemapper remapper =
      new ShadingRemapper(
          rule(
              "#io.opentelemetry.OpenTelemetry",
              "#io.opentelemetry.auto.shaded.io.opentelemetry.OpenTelemetry"),
          rule(
              "#io.opentelemetry.context",
              "#io.opentelemetry.auto.shaded.io.opentelemetry.context"),
          rule(
              "#io.opentelemetry.distributedcontext",
              "#io.opentelemetry.auto.shaded.io.opentelemetry.distributedcontext"),
          rule(
              "#io.opentelemetry.internal",
              "#io.opentelemetry.auto.shaded.io.opentelemetry.internal"),
          rule(
              "#io.opentelemetry.metrics",
              "#io.opentelemetry.auto.shaded.io.opentelemetry.metrics"),
          rule("#io.opentelemetry.trace", "#io.opentelemetry.auto.shaded.io.opentelemetry.trace"));

  public ExporterClassLoader(final URL[] urls, final ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  protected Class<?> findClass(final String name) throws ClassNotFoundException {

    // Use resource loading to get the class as a stream of bytes, then use ASM to retransform it.
    try (final InputStream in = getResourceAsStream(name.replace('.', '/') + ".class")) {
      final ClassWriter cw = new ClassWriter(0);
      final ClassReader cr = new ClassReader(in);
      cr.accept(new ClassRemapper(cw, remapper), ClassReader.EXPAND_FRAMES);
      final byte[] bytes = cw.toByteArray();
      return defineClass(name, bytes, 0, bytes.length);
    } catch (final IOException e) {
      throw new ClassNotFoundException(name);
    }
  }
}
