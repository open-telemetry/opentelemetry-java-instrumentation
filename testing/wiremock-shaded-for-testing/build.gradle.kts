plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
}

dependencies {
  // If tests fail when updating this, update the list of relocate based on any
  // class conflict reported in the failure.
  implementation("com.github.tomakehurst:wiremock-jre8:2.35.2")
  implementation("com.google.errorprone:error_prone_annotations")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.1")
}

tasks {
  shadowJar {
    dependencies {
      exclude(dependency("org.slf4j:slf4j-api"))
      exclude(dependency("org.junit.jupiter:junit-jupiter-api"))
      exclude(dependency("org.junit.platform:junit-platform-commons"))
      exclude(dependency("org.ow2.asm:asm"))
      // Exclude dependencies bundled during Armeria shading
      exclude(dependency("com.fasterxml.jackson.core:jackson-annotations"))
      exclude(dependency("com.fasterxml.jackson.core:jackson-core"))
      exclude(dependency("com.fasterxml.jackson.core:jackson-databind"))
    }

    // Ensures tests are not affected by wiremock dependencies. Wiremock itself is
    // safe with its original name.
    relocate("com.google.common", "io.opentelemetry.testing.internal.guava")
    relocate("org.apache.commons", "io.opentelemetry.testing.internal.apachecommons")
    relocate("org.apache.hc", "io.opentelemetry.testing.internal.apachehttp")
    relocate("org.eclipse.jetty", "io.opentelemetry.testing.internal.jetty")
    relocate("com.fasterxml.jackson", "io.opentelemetry.testing.internal.jackson")
    relocate("com.jayway.jsonpath", "io.opentelemetry.testing.internal.jsonpath")
    relocate("javax.servlet", "io.opentelemetry.testing.internal.servlet")
    relocate("org.yaml", "io.opentelemetry.testing.internal.yaml")

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
