### IntelliJ setup

**NB!** Please ensure that Intellij uses the same java installation as you do for building this
project from command line. This ensures that Gradle task avoidance and build cache work properly and
can greatly reduce build time.

Suggested plugins and settings:

* Editor > Code Style > Java/Groovy > Imports
  ![code style](https://user-images.githubusercontent.com/5099946/132923944-5737a731-351a-46a3-8fc4-5fe64e5a630c.png)
  * Class count to use import with '*': `9999` (some number sufficiently large that is unlikely to
    matter)
  * Names count to use static import with '*': `9999`
  * Default Import Layout should be used:
    ![import layout](https://user-images.githubusercontent.com/5099946/132924187-daf2df16-3e46-48db-9cf6-348252268a86.png)

* [Google Java Format](https://plugins.jetbrains.com/plugin/8527-google-java-format) plugin is
  suggested to be installed
  ![google format](https://user-images.githubusercontent.com/5099946/131758519-14d27c17-5fc2-4447-84b0-dbe7a7329022.png)
  Enable google-java-format
  ![enable google format](https://user-images.githubusercontent.com/5099946/131759832-36437aa0-a5f7-42c0-9425-8c5b45c16765.png)


* Install [Save Actions](https://plugins.jetbrains.com/plugin/7642-save-actions)

  ![save action](https://user-images.githubusercontent.com/5099946/131758940-7a1820db-3cf4-4e30-b346-c45c1ff4646e.png)
  Configure Save Actions as follows:
  ![Recommended Settings](save-actions.png)
