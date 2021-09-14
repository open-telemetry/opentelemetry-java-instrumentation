# IntelliJ setup and troubleshooting

**NB!** Please ensure that Intellij uses the same java installation as you do for building this
project from command line. This ensures that Gradle task avoidance and build cache work properly and
can greatly reduce build time.

Suggested plugins and settings:

## [google-java-format](https://plugins.jetbrains.com/plugin/8527-google-java-format)

Installation:

![google format](https://user-images.githubusercontent.com/5099946/131758519-14d27c17-5fc2-4447-84b0-dbe7a7329022.png)

Configuration:

![enable google format](https://user-images.githubusercontent.com/5099946/131759832-36437aa0-a5f7-42c0-9425-8c5b45c16765.png)


## [Save Actions](https://plugins.jetbrains.com/plugin/7642-save-actions)

Installation:

![save action](https://user-images.githubusercontent.com/5099946/131758940-7a1820db-3cf4-4e30-b346-c45c1ff4646e.png)

Configuration:

![Recommended Settings](save-actions.png)

## Troubleshooting

Occassionally, Intellij gets confused, maybe due to the number of modules in this project,
maybe due ot other reasons. In any case, here's some things that might help:

### Invalidate Caches > "Just restart"

* Go to File > Invalidate Caches...
* Unselect all the options
* Click the "Just restart" link

This seems to fix more issues than just closing and re-opening Intellij :shrug:.

### Delete your `.idea` directory

* Close Intellij
* Delete the `.idea` directory in the root directory of your local repository
* Open Intellij
