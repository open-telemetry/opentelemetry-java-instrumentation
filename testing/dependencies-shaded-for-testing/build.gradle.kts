plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
}

val denyUnsafe = gradle.startParameter.projectProperties["denyUnsafe"] == "true"

dependencies {
  implementation("com.linecorp.armeria:armeria-junit5:1.33.4")
  implementation("com.google.errorprone:error_prone_annotations")
  implementation("io.opentelemetry.proto:opentelemetry-proto")
  implementation("com.google.protobuf:protobuf-java-util:4.33.1")
  implementation("com.github.tomakehurst:wiremock-jre8:2.35.2")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  // we'll replace caffeine shaded in armeria with a later version that doesn't use Unsafe. Caffeine
  // 3+ doesn't work with Java 8, but that is fine since --sun-misc-unsafe-memory-access=deny
  // requires Java 23.
  if (denyUnsafe) {
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")
  }
}

tasks {
  shadowJar {
    dependencies {
      exclude(dependency("org.slf4j:slf4j-api"))
      exclude(dependency("org.junit.jupiter:junit-jupiter-api"))
      exclude(dependency("org.junit.platform:junit-platform-commons"))
      exclude(dependency("com.google.code.findbugs:annotations"))
    }

    // Ensures tests are not affected by Armeria instrumentation
    relocate("com.linecorp.armeria", "io.opentelemetry.testing.internal.armeria")
    if (denyUnsafe) {
      relocate(
        "com.github.benmanes.caffeine",
        "io.opentelemetry.testing.internal.armeria.internal.shaded.caffeine"
      )
    }
    relocate("com.fasterxml.jackson", "io.opentelemetry.testing.internal.jackson")
    relocate("net.bytebuddy", "io.opentelemetry.testing.internal.bytebuddy")
    relocate("reactor", "io.opentelemetry.testing.internal.reactor")
    relocate("com.aayushatharva.brotli4j", "io.opentelemetry.testing.internal.brotli4j")
    relocate("com.google.errorprone", "io.opentelemetry.testing.internal.errorprone")

    // Allows tests of Netty instrumentations which would otherwise conflict.
    // The relocation must end with io.netty to allow Netty to detect shaded native libraries.
    // https://github.com/netty/netty/blob/e69107ceaf247099ad9a198b8ef557bdff994a99/common/src/main/java/io/netty/util/internal/NativeLibraryLoader.java#L120
    relocate("io.netty", "io.opentelemetry.testing.internal.io.netty")
    exclude("META-INF/maven/**")
    relocate("META-INF/native/libnetty", "META-INF/native/libio_opentelemetry_testing_internal_netty")
    relocate("META-INF/native/netty", "META-INF/native/io_opentelemetry_testing_internal_netty")

    // relocate micrometer and its dependencies so that it doesn't conflict with instrumentation tests
    relocate("io.micrometer", "io.opentelemetry.testing.internal.io.micrometer")
    relocate("org.HdrHistogram", "io.opentelemetry.testing.internal.org.hdrhistogram")
    relocate("org.LatencyUtils", "io.opentelemetry.testing.internal.org.latencyutils")

    relocate("io.opentelemetry.proto", "io.opentelemetry.testing.internal.proto")
    relocate("com.google.protobuf", "io.opentelemetry.testing.internal.protobuf")
    relocate("com.google.gson", "io.opentelemetry.testing.internal.gson")
    relocate("com.google.common", "io.opentelemetry.testing.internal.guava")
    relocate("org.apache.commons", "io.opentelemetry.testing.internal.apachecommons")
    relocate("org.apache.hc", "io.opentelemetry.testing.internal.apachehttp")
    relocate("org.eclipse.jetty", "io.opentelemetry.testing.internal.jetty")
    relocate("com.fasterxml.jackson", "io.opentelemetry.testing.internal.jackson")
    relocate("com.jayway.jsonpath", "io.opentelemetry.testing.internal.jsonpath")
    relocate("javax.servlet", "io.opentelemetry.testing.internal.servlet")
    relocate("org.yaml", "io.opentelemetry.testing.internal.yaml")

    // don't relocate wiremock itself
    relocate("com.github.tomakehurst.wiremock", "com.github.tomakehurst.wiremock")

    mergeServiceFiles()
    // mergeServiceFiles requires that duplicate strategy is set to include
    filesMatching("META-INF/services/**") {
      duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    // exclude caffeine shaded in armeria
    if (denyUnsafe) {
      exclude("com/linecorp/armeria/internal/shaded/caffeine/**")
    }

    // relocate everything else
    enableAutoRelocation = true
    relocationPrefix = "io.opentelemetry.testing.internal"
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
