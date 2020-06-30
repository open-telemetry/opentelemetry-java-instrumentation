plugins {
  groovy
  `java-gradle-plugin`
  id("com.diffplug.gradle.spotless") version "4.3.0"
}

gradlePlugin {
  plugins {
    create("muzzle-plugin") {
      id = "muzzle"
      implementationClass = "MuzzlePlugin"
    }
  }
}

repositories {
  mavenLocal()
  jcenter()
  mavenCentral()
}

dependencies {
  compile(gradleApi())
  compile(localGroovy())

  compile("org.eclipse.aether", "aether-connector-basic", "1.1.0")
  compile("org.eclipse.aether", "aether-transport-http", "1.1.0")
  compile("org.apache.maven", "maven-aether-provider", "3.3.9")

  compile("com.google.guava", "guava", "20.0")
  compile("org.ow2.asm", "asm", "7.0-beta")
  compile("org.ow2.asm", "asm-tree", "7.0-beta")
  compile("org.apache.httpcomponents:httpclient:4.5.10")

  testCompile("org.spockframework", "spock-core", "1.3-groovy-2.5")
  testCompile("org.codehaus.groovy", "groovy-all", "2.5.8")
}
