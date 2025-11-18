plugins {
  id("java")
  id("com.diffplug.spotless") version "8.0.0"
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
  implementation(enforcedPlatform("org.junit:junit-bom:5.14.1"))

  testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.2"))
  testImplementation("org.testcontainers:testcontainers:2.0.2")
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testImplementation("com.squareup.okhttp3:okhttp:5.3.2")
  testImplementation("org.jooq:joox:2.0.1")
  testImplementation("com.jayway.jsonpath:json-path:2.10.0")
  testImplementation("org.slf4j:slf4j-simple:2.0.17")

  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
  test {
    useJUnitPlatform()
  }
}
