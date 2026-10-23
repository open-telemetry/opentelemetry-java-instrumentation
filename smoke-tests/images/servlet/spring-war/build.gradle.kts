import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.War
import org.gradle.api.tasks.bundling.Zip

plugins {
  war

  id("otel.java-conventions")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

val commonSpring =
  configurations.create("commonSpring") {
    isCanBeConsumed = false
    isCanBeResolved = true
  }

dependencies {
  implementation("org.springframework:spring-webmvc:5.2.25.RELEASE")
  compileOnly("javax.servlet:javax.servlet-api:4.0.1")

  add(commonSpring.name, "org.springframework:spring-webmvc:5.3.39")
}

tasks.register<War>("probeWar") {
  archiveFileName.set("probe.war")
}

tasks.register<Zip>("sharedWar") {
  archiveFileName.set("shared.war")
  from(sourceSets.main.get().output) {
    into("WEB-INF/classes")
  }
  from("src/main/webapp/WEB-INF") {
    into("WEB-INF")
    exclude("root.xml")
  }
  from("src/shared-webapp/WEB-INF/root.xml") {
    into("WEB-INF")
  }
}

tasks.register<Tar>("commonSpringLibs") {
  archiveFileName.set("common-spring-libs.tar.gz")
  compression = Compression.GZIP
  from(commonSpring)
}
