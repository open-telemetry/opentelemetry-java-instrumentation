/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

final class JarAnalyzerUtil {

  static final String JAR_EXTENSION = ".jar";

  private static final AttributeKey<String> PACKAGE_NAME = AttributeKey.stringKey("package.name");
  private static final AttributeKey<String> PACKAGE_VERSION =
      AttributeKey.stringKey("package.version");
  private static final AttributeKey<String> PACKAGE_TYPE = AttributeKey.stringKey("package.type");
  private static final AttributeKey<String> PACKAGE_DESCRIPTION =
      AttributeKey.stringKey("package.description");
  private static final AttributeKey<String> PACKAGE_CHECKSUM =
      AttributeKey.stringKey("package.checksum");
  private static final AttributeKey<String> PACKAGE_PATH = AttributeKey.stringKey("package.path");

  /** Set the attributes {@link #PACKAGE_TYPE} to {@code jar}. */
  static void addPackageType(AttributesBuilder builder) {
    builder.put(PACKAGE_TYPE, "jar");
  }

  /**
   * Set the attributes {@link #PACKAGE_CHECKSUM} from the {@code jarUrl}.
   *
   * <p>The {@link #PACKAGE_CHECKSUM} is set to the SHA-1 checksum of the JAR, e.g. {@code
   * 30d16ec2aef6d8094c5e2dce1d95034ca8b6cb42}.
   */
  static void addPackageChecksum(AttributesBuilder builder, URL jarUrl) throws IOException {
    builder.put(PACKAGE_CHECKSUM, computeSha1(jarUrl));
  }

  private static String computeSha1(URL jarUrl) throws IOException {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unexpected error. Checksum algorithm SHA1 does not exist.", e);
    }

    try (InputStream is = new DigestInputStream(jarUrl.openStream(), md)) {
      byte[] buffer = new byte[1024 * 8];
      // read in the stream in chunks while updating the digest
      while (is.read(buffer) != -1) {}

      byte[] mdbytes = md.digest();

      // convert to hex format
      StringBuilder sb = new StringBuilder(40);
      for (byte mdbyte : mdbytes) {
        sb.append(Integer.toString((mdbyte & 0xff) + 0x100, 16).substring(1));
      }

      return sb.toString();
    }
  }

  /**
   * Set the attributes {@link #PACKAGE_PATH} from the {@code jarUrl}.
   *
   * <p>The {@link #PACKAGE_PATH} is set to the jar file name, e.g. {@code
   * jackson-datatype-jsr310-2.15.2.jar}.
   */
  static void addPackagePath(AttributesBuilder builder, URL jarUrl) throws Exception {
    builder.put(PACKAGE_PATH, jarFilename(jarUrl));
  }

  private static String jarFilename(URL jarUrl) throws Exception {
    String path = jarUrl.getFile();
    int end = path.lastIndexOf(JAR_EXTENSION);
    if (end > 0) {
      path = path.substring(0, end);
      int start = path.lastIndexOf(File.separator);
      if (start > -1) {
        return path.substring(start + 1) + JAR_EXTENSION;
      }
      return path + JAR_EXTENSION;
    }
    throw new Exception("Cannot extract jar file name from jar URL: " + jarUrl);
  }

  /**
   * Set the attributes {@link #PACKAGE_DESCRIPTION} from the {@code jarUrl}.
   *
   * <p>The {@link #PACKAGE_DESCRIPTION} is set to manifest "{Implementation-Title} by
   * {Implementation-Vendor}", e.g. {@code Jackson datatype: JSR310 by FasterXML}.
   */
  static void addPackageDescription(AttributesBuilder builder, URL jarUrl) throws IOException {
    try (InputStream inputStream = jarUrl.openStream();
        JarInputStream jarInputStream = new JarInputStream(inputStream)) {
      Manifest manifest = jarInputStream.getManifest();
      if (manifest == null) {
        return;
      }

      java.util.jar.Attributes mainAttributes = manifest.getMainAttributes();
      String name = mainAttributes.getValue(java.util.jar.Attributes.Name.IMPLEMENTATION_TITLE);
      String description =
          mainAttributes.getValue(java.util.jar.Attributes.Name.IMPLEMENTATION_VENDOR);

      String packageDescription = name;
      if (description != null && !description.isEmpty()) {
        packageDescription += " by " + description;
      }
      builder.put(PACKAGE_DESCRIPTION, packageDescription);
    }
  }

  /**
   * Set the attributes {@link #PACKAGE_NAME} and {@link #PACKAGE_VERSION} from the {@code jarUrl}.
   *
   * <p>The {@link #PACKAGE_NAME} is set to the POM "{groupId}:{artifactId}", e.g. {@code Jackson
   * datatype: JSR310 by FasterXML}.
   *
   * <p>The {@link #PACKAGE_VERSION} is set to the POM "{version}", e.g. {@code 2.15.2}.
   */
  static void addPackageNameAndVersion(AttributesBuilder builder, URL jarUrl) throws IOException {
    Properties pom = null;
    try (InputStream inputStream = jarUrl.openStream();
        JarInputStream jarInputStream = new JarInputStream(inputStream)) {
      // Advance the jarInputStream to the pom.properties entry
      for (JarEntry entry = jarInputStream.getNextJarEntry();
          entry != null;
          entry = jarInputStream.getNextJarEntry()) {
        if (entry.getName().startsWith("META-INF/maven")
            && entry.getName().endsWith("pom.properties")) {
          if (pom != null) {
            // we've found multiple pom files. bail!
            return;
          }
          pom = new Properties();
          pom.load(jarInputStream);
        }
      }
    }
    if (pom == null) {
      return;
    }
    String groupId = pom.getProperty("groupId");
    String artifactId = pom.getProperty("artifactId");
    if (groupId != null && !groupId.isEmpty() && artifactId != null && !artifactId.isEmpty()) {
      builder.put(PACKAGE_NAME, groupId + ":" + artifactId);
    }
    String version = pom.getProperty("version");
    if (version != null && !version.isEmpty()) {
      builder.put(PACKAGE_VERSION, version);
    }
  }

  private JarAnalyzerUtil() {}
}
