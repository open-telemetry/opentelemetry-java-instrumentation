/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.ShadingRemapper.rule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.commons.ClassRemapper;

public class RemappingUrlConnection extends URLConnection {
  // We need to prefix the names to prevent the gradle shadowJar relocation rules from touching
  // them. It's possible to do this by excluding this class from shading, but it may cause issue
  // with transitive dependencies down the line.
  private static final ShadingRemapper remapper =
      new ShadingRemapper(
          rule("#io.opentelemetry.api", "#io.opentelemetry.javaagent.shaded.io.opentelemetry.api"),
          rule(
              "#io.opentelemetry.context",
              "#io.opentelemetry.javaagent.shaded.io.opentelemetry.context"),
          rule(
              "#io.opentelemetry.extension.aws",
              "#io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.aws"),
          rule("#java.util.logging.Logger", "#io.opentelemetry.javaagent.bootstrap.PatchLogger"),
          rule("#org.slf4j", "#io.opentelemetry.javaagent.slf4j"));

  private final JarFile delegateJarFile;
  private final JarEntry entry;

  private InputStream cachedResult;

  public RemappingUrlConnection(URL url, JarFile delegateJarFile, JarEntry entry) {
    super(url);
    this.delegateJarFile = delegateJarFile;
    this.entry = entry;
  }

  @Override
  public void connect() {
    connected = true;
  }

  @Override
  public InputStream getInputStream() {
    if (cachedResult == null) {
      cachedResult = readAndRemap();
    }

    return cachedResult;
  }

  private InputStream readAndRemap() {
    try {
      InputStream inputStream = delegateJarFile.getInputStream(entry);
      byte[] remappedClass = remapClassBytes(inputStream);
      return new ByteArrayInputStream(remappedClass);
    } catch (IOException e) {
      System.err.printf("Failed to remap bytes for %s: %s%n", url.toString(), e.getMessage());
      return new ByteArrayInputStream(new byte[0]);
    }
  }

  private static byte[] remapClassBytes(InputStream in) throws IOException {
    ClassReader cr = new ClassReader(in);
    ClassWriter cw = new ClassWriter(cr, 0);
    cr.accept(new ClassRemapper(cw, remapper), ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  @Override
  public Permission getPermission() {
    // No permissions needed because all classes are in memory
    return null;
  }
}
