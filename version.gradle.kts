val stableVersion = "2.33.0"
val alphaVersion = "2.33.0-alpha"

val apidiffBaselineVersion = "2.32.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
