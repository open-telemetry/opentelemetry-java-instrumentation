/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import javax.annotation.Nullable;

/**
 * For a given URL representing a Jar directly on the file system or embedded within another
 * archive, this class provides methods which expose useful information about it.
 */
class JarDetails {
  static final String JAR_EXTENSION = "jar";
  static final String WAR_EXTENSION = "war";
  static final String EAR_EXTENSION = "ear";
  private static final String JAR_ENTRY_SEPARATOR = "!/";
  private static final ThreadLocal<MessageDigest> SHA1 =
      ThreadLocal.withInitial(
          () -> {
            try {
              return MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException e) {
              throw new IllegalStateException(e);
            }
          });

  private final URL url;
  @Nullable private final Properties pom;
  @Nullable private final Manifest manifest;
  private final String sha1Checksum;

  private JarDetails(
      URL url, @Nullable Properties pom, @Nullable Manifest manifest, String sha1Checksum) {
    this.url = url;
    this.pom = pom;
    this.manifest = manifest;
    this.sha1Checksum = sha1Checksum;
  }

  static JarDetails forUrl(URL url) throws IOException {
    if (url.getProtocol().equals("jar")) {
      String urlString = url.toExternalForm();
      String urlLower = urlString.toLowerCase(Locale.ROOT);
      int index = urlLower.indexOf(JAR_ENTRY_SEPARATOR);
      if (index > 0) {
        String targetEntry = urlString.substring(index + JAR_ENTRY_SEPARATOR.length());
        try (JarFile jarFile = new JarFile(urlString.substring("jar:file:".length(), index))) {
          JarEntry jarEntry = jarFile.getJarEntry(targetEntry);
          if (jarEntry != null) {
            return createEmbedded(url, jarFile, jarEntry);
          }
          throw new IOException("Embedded jar entry not found: " + targetEntry);
        }
      }
    }
    try (JarFile jarFile = new JarFile(url.getFile())) {
      return new JarDetails(
          url, getPom(jarFile), getManifest(jarFile), computeDigest(url.openStream(), SHA1.get()));
    }
  }

  /**
   * Returns the archive file name, e.g. {@code jackson-datatype-jsr310-2.15.2.jar}. Returns null if
   * unable to identify the file name from {@link #url}.
   */
  @Nullable
  String packagePath() {
    String path = url.getFile();
    int start = path.lastIndexOf('/');
    if (start > -1) {
      return path.substring(start + 1);
    }
    return null;
  }

  /**
   * Returns the extension of the archive, e.g. {@code jar}. Returns null if unable to identify the
   * extension from {@link #url}
   */
  @Nullable
  String packageType() {
    String path = url.getFile();
    int extensionStart = path.lastIndexOf(".");
    if (extensionStart > -1) {
      return path.substring(extensionStart + 1);
    }
    return null;
  }

  /**
   * Returns the maven package name in the format {@code groupId:artifactId}, e.g. {@code
   * com.fasterxml.jackson.datatype:jackson-datatype-jsr310}. Returns null if {@link #pom} is not
   * found, or is missing groupId or artifactId properties.
   */
  @Nullable
  String packageName() {
    if (pom == null) {
      return null;
    }
    String groupId = pom.getProperty("groupId");
    String artifactId = pom.getProperty("artifactId");
    if (groupId != null && !groupId.isEmpty() && artifactId != null && !artifactId.isEmpty()) {
      return groupId + ":" + artifactId;
    }
    return null;
  }

  /**
   * Returns the version from the pom file, e.g. {@code 2.15.2}. Returns null if {@link #pom} is not
   * found or is missing version property.
   */
  @Nullable
  String version() {
    if (pom == null) {
      return null;
    }
    String version = pom.getProperty("version");
    if (version != null && !version.isEmpty()) {
      return version;
    }
    return null;
  }

  /**
   * Returns the package description from the jar manifest "{Implementation-Title} by
   * {Implementation-Vendor}", e.g. {@code Jackson datatype: JSR310 by FasterXML}. Returns null if
   * {@link #manifest} is not found.
   */
  @Nullable
  String packageDescription() {
    if (manifest == null) {
      return null;
    }

    Attributes mainAttributes = manifest.getMainAttributes();
    String name = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
    String vendor = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);

    if (name == null || name.isEmpty()) {
      return vendor == null || vendor.isEmpty() ? null : vendor;
    }
    if (vendor == null || vendor.isEmpty()) {
      return name;
    }
    return name + " by " + vendor;
  }

  /** Returns the SHA1 hash of this file, e.g. {@code 30d16ec2aef6d8094c5e2dce1d95034ca8b6cb42}. */
  String computeSha1() {
    return sha1Checksum;
  }

  private static JarDetails createEmbedded(URL url, JarFile jarFile, JarEntry jarEntry)
      throws IOException {
    return new JarDetails(
        url,
        getPom(jarFile, jarEntry),
        getManifest(jarFile, jarEntry),
        computeDigest(jarFile.getInputStream(jarEntry), SHA1.get()));
  }

  private static String computeDigest(InputStream inputStream, MessageDigest md)
      throws IOException {
    try {
      md.reset();
      DigestInputStream dis = new DigestInputStream(inputStream, md);
      byte[] buffer = new byte[8192];
      while (dis.read(buffer) != -1) {}
      byte[] digest = md.digest();
      return String.format(Locale.ROOT, "%040x", new BigInteger(1, digest));
    } finally {
      inputStream.close();
    }
  }

  @Nullable
  private static Manifest getManifest(JarFile jarFile) {
    try {
      return jarFile.getManifest();
    } catch (IOException e) {
      return null;
    }
  }

  @Nullable
  private static Manifest getManifest(JarFile jarFile, JarEntry jarEntry) {
    try (JarInputStream in = new JarInputStream(jarFile.getInputStream(jarEntry))) {
      return in.getManifest();
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Returns the values from pom.properties if this file is found. If multiple pom.properties files
   * are found or there is an error reading the file, return null.
   */
  @Nullable
  private static Properties getPom(JarFile jarFile) throws IOException {
    Properties pom = null;
    for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
      JarEntry jarEntry = entries.nextElement();
      if (jarEntry.getName().startsWith("META-INF/maven")
          && jarEntry.getName().endsWith("pom.properties")) {
        if (pom != null) {
          // we've found multiple pom files. bail!
          return null;
        }
        Properties props = new Properties();
        try (InputStream in = jarFile.getInputStream(jarEntry)) {
          props.load(in);
          pom = props;
        }
      }
    }
    return pom;
  }

  @Nullable
  private static Properties getPom(JarFile jarFile, JarEntry jarEntry) throws IOException {
    Properties pom = null;
    // Need to navigate inside the embedded jar which can't be done via random access.
    try (JarInputStream in = new JarInputStream(jarFile.getInputStream(jarEntry))) {
      for (JarEntry entry = in.getNextJarEntry(); entry != null; entry = in.getNextJarEntry()) {
        if (entry.getName().startsWith("META-INF/maven")
            && entry.getName().endsWith("pom.properties")) {
          if (pom != null) {
            // we've found multiple pom files. bail!
            return null;
          }
          Properties props = new Properties();
          props.load(in);
          pom = props;
        }
      }
      return pom;
    }
  }
}
