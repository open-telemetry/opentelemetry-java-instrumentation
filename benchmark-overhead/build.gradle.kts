plugins {
  id("java")
  id("com.diffplug.spotless") version "6.19.0"
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
  implementation(enforcedPlatform("org.junit:junit-bom:5.9.3"))

  testImplementation("org.testcontainers:testcontainers:1.18.1")
  testImplementation("org.testcontainers:postgresql:1.18.2")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testImplementation("com.squareup.okhttp3:okhttp:4.11.0")
  testImplementation("org.jooq:joox:2.0.0")
  testImplementation("com.jayway.jsonpath:json-path:2.8.0")
  testImplementation("org.slf4j:slf4j-simple:2.0.7")

  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks {
  test {
    useJUnitPlatform()
  }
}
