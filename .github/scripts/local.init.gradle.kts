settingsEvaluated {
  settings.pluginManagement {
    repositories {
      mavenLocal()
      removeIf { it.name == "sonatype" }
    }
  }
}

allprojects {
  buildscript {
    repositories {
      mavenLocal()
      removeIf { it.name == "sonatype" }
    }
  }
  repositories {
    mavenLocal()
    removeIf { it.name == "sonatype" }
  }
}
