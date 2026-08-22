plugins {
  java
  id("com.diffplug.spotless")
}

version = rootProject.version

repositories {
  mavenCentral()
  maven {
    name = "sonatype"
    url = uri("https://central.sonatype.com/repository/maven-snapshots/")
  }
}

// Spotless keeps this example self-contained when it is copied out of this repository.
// Copied projects may optionally use Flint instead.
spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("spotless.license.java"), "(package|import|public)")
    target("src/**/*.java")
  }
}

dependencies {
  implementation(platform("io.opentelemetry:opentelemetry-bom:$opentelemetrySdkVersion"))

  // these serve as a test of the instrumentation boms
  implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:$opentelemetryJavaagentVersion"))
  implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:$opentelemetryJavaagentAlphaVersion"))

  testImplementation("org.mockito:mockito-core:5.23.0")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.14.4"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
  test {
    useJUnitPlatform()
  }

  compileJava {
    options.release.set(8)
  }
}
