val stableVersion = "2.17.0"
val alphaVersion = "2.17.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
