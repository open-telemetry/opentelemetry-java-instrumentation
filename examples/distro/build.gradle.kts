group = "io.opentelemetry.example"
version = "1.0-SNAPSHOT"

buildscript {
  repositories {
    maven {
      url = uri("https://plugins.gradle.org/m2/")
    }
    maven {
      name = "sonatype"
      url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
  }
  dependencies {
    classpath("com.diffplug.spotless:spotless-plugin-gradle:8.1.0")
    classpath("com.gradleup.shadow:shadow-gradle-plugin:9.3.1")
    classpath("io.opentelemetry.instrumentation:gradle-plugins:2.25.0-alpha-SNAPSHOT")
  }
}

subprojects {
  version = rootProject.version

  apply(plugin = "java")
  apply(plugin = "com.diffplug.spotless")

  val versions = mapOf(
    // this line is managed by .github/scripts/update-sdk-version.sh
    "opentelemetrySdk" to "1.58.0",

    // these lines are managed by .github/scripts/update-version.sh
    "opentelemetryJavaagent" to "2.25.0-SNAPSHOT",
    "opentelemetryJavaagentAlpha" to "2.25.0-alpha-SNAPSHOT",

    "autoservice" to "1.1.1"
  )

  val deps = mapOf(
    "autoservice" to listOf(
      "com.google.auto.service:auto-service:${versions["autoservice"]}",
      "com.google.auto.service:auto-service-annotations:${versions["autoservice"]}"
    )
  )

  extra["versions"] = versions
  extra["deps"] = deps

  repositories {
    mavenCentral()
    maven {
      name = "sonatype"
      url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
  }

  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
      googleJavaFormat()
      licenseHeaderFile(rootProject.file("../../buildscripts/spotless.license.java"), "(package|import|public)")
      target("src/**/*.java")
    }
  }

  dependencies {
    implementation(platform("io.opentelemetry:opentelemetry-bom:${versions.getValue("opentelemetrySdk")}"))

    // these serve as a test of the instrumentation boms
    implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:${versions.getValue("opentelemetryJavaagent")}"))
    implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:${versions.getValue("opentelemetryJavaagentAlpha")}"))

    testImplementation("org.mockito:mockito-core:5.21.0")

    testImplementation(enforcedPlatform("org.junit:junit-bom:5.14.2"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  }

  tasks.test {
    useJUnitPlatform()
  }

  tasks.compileJava {
    options.release.set(8)
  }
}
