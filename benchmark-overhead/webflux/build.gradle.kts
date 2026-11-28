plugins {
  java
  id("org.springframework.boot") version "4.1.2"
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("io.micrometer:context-propagation")
  runtimeOnly("org.postgresql:r2dbc-postgresql")
}

tasks.bootJar {
  archiveFileName.set("webflux-benchmark.jar")
}
