/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;

/** Opt-in workload controls for the standalone Muzzle benchmark. */
public final class MuzzleBenchmarkSupport {
  private static final Map<FileStamp, String> ARTIFACT_HASHES = new ConcurrentHashMap<>();

  private record FileStamp(Path path, long size, FileTime modified) {}

  private MuzzleBenchmarkSupport() {}

  public static RepositorySystemSession repositorySession(
      RepositorySystem system, File repository, boolean offline) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    session.setLocalRepositoryManager(
        system.newLocalRepositoryManager(session, new LocalRepository(repository)));
    session.setOffline(offline);
    return session;
  }

  public static void requireVersionMetadata(
      RepositorySystem system,
      RepositorySystemSession session,
      VersionRangeResult result,
      boolean intentionallyEmpty)
      throws VersionRangeResolutionException {
    if (!result.getVersions().isEmpty() || intentionallyEmpty) {
      return;
    }
    VersionRangeRequest request = result.getRequest();
    VersionRangeResult allVersions =
        system.resolveVersionRange(
            session,
            new VersionRangeRequest()
                .setArtifact(request.getArtifact().setVersion("[0,)"))
                .setRepositories(request.getRepositories())
                .setRequestContext(request.getRequestContext()));
    if (allVersions.getVersions().isEmpty()) {
      throw new IllegalStateException(
          "Missing Muzzle benchmark version metadata for " + result.getRequest().getArtifact());
    }
  }

  public static kotlin.random.Random randomFor(String identity, long seed) {
    return kotlin.random.RandomKt.Random(seed ^ identity.hashCode());
  }

  public static void record(
      File directory,
      String taskPath,
      boolean assertPass,
      Set<String> excludedNames,
      Set<String> excludedModules,
      ArtifactCollection artifacts)
      throws IOException {
    Files.createDirectories(directory.toPath());
    Files.write(
        reportFile(directory, taskPath).toPath(),
        manifest(taskPath, assertPass, excludedNames, excludedModules, artifacts),
        UTF_8);
  }

  public static File reportFile(File directory, String taskPath) {
    String filename = hex(digest().digest(taskPath.getBytes(UTF_8))) + ".txt";
    return new File(directory, filename);
  }

  public static List<String> manifest(
      String taskPath,
      boolean assertPass,
      Set<String> excludedNames,
      Set<String> excludedModules,
      ArtifactCollection artifacts)
      throws IOException {
    List<String> entries = new ArrayList<>();
    Map<File, Integer> classpathOrder = new HashMap<>();
    for (File file : artifacts.getArtifactFiles()) {
      classpathOrder.put(file, classpathOrder.size());
    }
    for (ResolvedArtifactResult artifact : artifacts.getArtifacts()) {
      Path file = artifact.getFile().toPath();
      BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
      FileStamp stamp = new FileStamp(file, attributes.size(), attributes.lastModifiedTime());
      String hash = ARTIFACT_HASHES.get(stamp);
      if (hash == null) {
        hash = hashFile(file);
        ARTIFACT_HASHES.put(stamp, hash);
      }
      entries.add(
          "artifact\t"
              + classpathOrder.get(artifact.getFile())
              + "\t"
              + artifact.getId().getComponentIdentifier().getDisplayName()
              + "\t"
              + file.getFileName()
              + "\t"
              + hash);
    }
    excludedNames.forEach(name -> entries.add("excluded-name\t" + name));
    excludedModules.forEach(module -> entries.add("excluded-module\t" + module));
    for (Throwable failure : artifacts.getFailures()) {
      entries.add(
          "unresolved\t"
              + failure.getClass().getName()
              + "\t"
              + String.valueOf(failure.getMessage())
                  .replace("\\", "\\\\")
                  .replace("\r", "\\r")
                  .replace("\n", "\\n"));
    }
    Collections.sort(entries);
    entries.add(0, "assertPass\t" + assertPass);
    entries.add(0, taskPath);
    return List.copyOf(entries);
  }

  private static String hashFile(Path file) throws IOException {
    MessageDigest digest = digest();
    try (InputStream input = Files.newInputStream(file)) {
      byte[] buffer = new byte[16384];
      int count;
      while ((count = input.read(buffer)) != -1) {
        digest.update(buffer, 0, count);
      }
    }
    return hex(digest.digest());
  }

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", e);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      result.append(Character.forDigit((value & 0xff) >>> 4, 16));
      result.append(Character.forDigit(value & 0xf, 16));
    }
    return result.toString();
  }
}
