plugins {
  id("java")
  id("com.diffplug.spotless") version "7.0.2"
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
  implementation(enforcedPlatform("org.junit:junit-bom:5.12.0"))

  testImplementation("org.testcontainers:testcontainers:1.20.5")
  testImplementation("org.testcontainers:postgresql:1.20.5")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
  testImplementation("org.jooq:joox:2.0.1")
  testImplementation("com.jayway.jsonpath:json-path:2.9.0")
  testImplementation("org.slf4j:slf4j-simple:2.0.16")

  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks {
  test {
    useJUnitPlatform()
  }
}
