#!/bin/bash -e

grep "val stableVersion = " version.gradle.kts | grep -Eo "[0-9]+.[0-9]+.[0-9]+"
