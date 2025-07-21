plugins {
  id("java")
  id("com.diffplug.spotless") version "7.2.0"
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("../buildscripts/spotless.license.java"), "(package|import|public)")
    target("src/**/*.java")
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(enforcedPlatform("org.junit:junit-bom:5.13.3"))

  testImplementation("org.testcontainers:testcontainers:1.21.3")
  testImplementation("org.testcontainers:postgresql:1.21.3")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testImplementation("com.squareup.okhttp3:okhttp:5.1.0")
  testImplementation("org.jooq:joox:2.0.1")
  testImplementation("com.jayway.jsonpath:json-path:2.9.0")
  testImplementation("org.slf4j:slf4j-simple:2.0.17")

  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
  test {
    useJUnitPlatform()
  }
}
