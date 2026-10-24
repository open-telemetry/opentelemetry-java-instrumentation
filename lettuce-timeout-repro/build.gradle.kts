plugins {
  id("java")
  id("org.springframework.boot") version "4.1.1"
  id("com.diffplug.spotless") version "8.10.4"
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("../buildscripts/spotless.license.java"), "(package|import|public)")
    target("src/**/*.java")
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

  constraints {
    // the version reported in https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/19814
    implementation("io.lettuce:lettuce-core") {
      version {
        strictly(providers.gradleProperty("lettuceVersion").orElse("7.7.0.RELEASE").get())
      }
    }
  }
}

tasks {
  bootJar {
    archiveFileName.set("lettuce-timeout-repro.jar")
  }
}
