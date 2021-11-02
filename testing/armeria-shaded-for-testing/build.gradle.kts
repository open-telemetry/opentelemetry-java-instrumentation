plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

dependencies {
  implementation("com.linecorp.armeria:armeria-junit5:1.8.0") {
    // We don't use JSON features of Armeria but shading it in can cause version conflicts with
    // instrumentation tests that do.
    exclude("com.fasterxml.jackson.core")

    // exclude micrometer so that it doesn't conflict with instrumentation tests
    exclude("io.micrometer")
  }
}

tasks {
  shadowJar {
    // Ensures tests are not affected by Armeria instrumentation
    relocate("com.linecorp.armeria", "io.opentelemetry.testing.internal.armeria")
    // Allows tests of Netty instrumentations which would otherwise conflict.
    // The relocation must end with io.netty to allow Netty to detect shaded native libraries.
    // https://github.com/netty/netty/blob/e69107ceaf247099ad9a198b8ef557bdff994a99/common/src/main/java/io/netty/util/internal/NativeLibraryLoader.java#L120
    relocate("io.netty", "io.opentelemetry.testing.internal.io.netty")
    exclude("META-INF/maven/**")
    relocate("META-INF/native/libnetty", "META-INF/native/libio_opentelemetry_testing_internal_netty")
    relocate("META-INF/native/netty", "META-INF/native/io_opentelemetry_testing_internal_netty")
    mergeServiceFiles()
  }

  val extractShadowJar by registering(Copy::class) {
    dependsOn(shadowJar)
    // there's both "LICENSE" file and "license" and without excluding one of these build fails on case insensitive file systems
    // there's a LICENSE.txt file that has the same contents anyway, so we're not losing anything excluding that
    from(zipTree(shadowJar.get().archiveFile)) {
      exclude("META-INF/LICENSE")
    }
    into("build/extracted/shadow")
    includeEmptyDirs = false
  }
}
