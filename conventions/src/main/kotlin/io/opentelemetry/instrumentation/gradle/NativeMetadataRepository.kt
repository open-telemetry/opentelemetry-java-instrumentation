/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

abstract class NativeMetadataRepository : ValueSource<URI, NativeMetadataRepository.Parameters> {
  interface Parameters : ValueSourceParameters {
    val archive: RegularFileProperty
    val destination: DirectoryProperty
  }

  override fun obtain(): URI {
    val archive = parameters.archive.get().asFile.toPath()
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(archive).use { input ->
      val buffer = ByteArray(8192)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
      }
    }
    val hash = digest.digest().joinToString("") { "%02x".format(it) }
    val destination = parameters.destination.get().asFile.toPath()
    Files.createDirectories(destination)
    val copy = destination.resolve("$hash.zip")
    if (!Files.exists(copy)) {
      val temporary = Files.createTempFile(destination, "metadata-", ".zip")
      try {
        Files.copy(archive, temporary, StandardCopyOption.REPLACE_EXISTING)
        Files.move(temporary, copy, StandardCopyOption.REPLACE_EXISTING)
      } finally {
        Files.deleteIfExists(temporary)
      }
    }
    return copy.toUri()
  }
}
