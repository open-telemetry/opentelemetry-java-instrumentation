settingsEvaluated {
  settings.pluginManagement {
    repositories {
      mavenLocal()
      removeIf { it.name == "sonatype" }
    }
  }
}

allprojects {
  repositories {
    mavenLocal()
    removeIf { it.name == "sonatype" }
  }
}
