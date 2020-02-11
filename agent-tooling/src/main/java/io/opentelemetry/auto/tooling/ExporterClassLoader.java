package io.opentelemetry.auto.tooling;

import static io.opentelemetry.auto.tooling.ShadingRemapper.rule;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.commons.ClassRemapper;
import net.bytebuddy.pool.TypePool;

public class ExporterClassLoader extends URLClassLoader {
  private final ShadingRemapper remapper =
      new ShadingRemapper(
          rule(
              "io.opentelemetry.OpenTelemetry",
              "io.opentelemetry.auto.shaded.io.opentelemetry.OpenTelemetry"),
          rule("io.opentelemetry.context", "io.opentelemetry.auto.shaded.io.opentelemetry.context"),
          rule(
              "io.opentelemetry.distributedcontext",
              "io.opentelemetry.auto.shaded.io.opentelemetry.distributedcontext"),
          rule(
              "io.opentelemetry.internal",
              "io.opentelemetry.auto.shaded.io.opentelemetry.internal"),
          rule("io.opentelemetry.metrics", "io.opentelemetry.auto.shaded.io.opentelemetry.metrics"),
          rule("io.opentelemetry.trace", "io.opentelemetry.auto.shaded.io.opentelemetry.trace"));

  public ExporterClassLoader(final URL[] urls, final ClassLoader parent) {
    super(urls, parent);
  }

  public ExporterClassLoader(final URL[] urls) {
    super(urls);
  }

  public ExporterClassLoader(
      final URL[] urls, final ClassLoader parent, final URLStreamHandlerFactory factory) {
    super(urls, parent, factory);
  }

  @Override
  protected Class<?> findClass(final String name) throws ClassNotFoundException {
    final Class<?> cl = super.findClass(name);
    return new ByteBuddy()
        .redefine(cl)
        .visit(
            new AsmVisitorWrapper.AbstractBase() {
              @Override
              public ClassVisitor wrap(
                  final TypeDescription instrumentedType,
                  final ClassVisitor classVisitor,
                  final Implementation.Context implementationContext,
                  final TypePool typePool,
                  final FieldList<FieldDescription.InDefinedShape> fields,
                  final MethodList<?> methods,
                  final int writerFlags,
                  final int readerFlags) {
                return new ClassRemapper(classVisitor, remapper);
              }
            })
        .make()
        .load(this, ClassReloadingStrategy.fromInstalledAgent())
        .getLoaded();
  }
}
