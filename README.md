## Operations

### Running The Application

#### Production Mode

1. Start the backend: `./gradlew -Dapplication.production=true :application:backend:run`

2. Start a Compose Web frontend: `./gradlew :application:frontend-ui:jsBrowserProductionRun`

3. Start one or more desktop frontends: `./gradlew :application:frontend-ui:run`

#### Development Mode

1. Start the backend in development mode: `./gradlew :application:backend:run`

2. Start a Compose Web frontend: `./gradlew :application:frontend-ui:jsBrowserDevelopmentRun`

3. Start additional web frontends on http://localhost:8081/.

4. Start one or more desktop frontends: `./gradlew :application:frontend-ui:run`

### Running Tests

#### Regular Tests

* `./gradlew --no-build-cache --no-configuration-cache --continue -p application/integration-test cleanJvmTest jvmTest`
* `./gradlew --no-build-cache --no-configuration-cache -p application/integration-test cleanJvmTest jvmTest --tests E2EUpdateRejectionTests`
* `./gradlew --no-build-cache --no-configuration-cache -p application/integration-test cleanJvmTest jvmTest --tests E2EMassUpdateTests`

#### Extended Tests

* `./gradlew --no-build-cache --no-configuration-cache -p application/integration-test -Dapplication.test.variant=extended cleanJvmTest jvmTest`
* `./gradlew --no-build-cache --no-configuration-cache -p application/integration-test -Dapplication.test.variant=extended cleanJvmTest jvmTest --tests E2EUpdateRejectionTests`
* `./gradlew --no-build-cache --no-configuration-cache -p application/integration-test -Dapplication.test.variant=extended cleanJvmTest jvmTest --tests E2EMassUpdateTests`

#### Code Coverage

Generate code coverage data by running the Gradle task `integrationJvmTest` (or any other relevant one) with
the `application.coverage=true` system property set:

* `./gradlew -Dapplication.coverage=true cleanAllTests allTests`
* `./gradlew -Dapplication.coverage=true -p application/integration-test cleanAllTests allTests`

NOTE: Kover generates coverage data for main classes only, not test classes.

To show coverage data in IDEA:

1. Select menu item _Run – Show Coverage Data_ (Ctrl+Alt+F6).

2. Add files found via `find application -name \*.ic`.

3. Select _Show Coverage_.

### Monitoring, Runtime Analysis

* CPU, Threads, Memory in IntelliJ IDEA
    * Integrated Window, all options: _Profiler_ Tool Window -> Right click on
      process -> [CPU and Memory Live Charts](https://www.jetbrains.com/help/idea/cpu-and-memory-live-charts.html)
    * Separate window, minimal
      options: `Ctrl+Shift+A` [CPU and Memory Live Charts](https://www.jetbrains.com/help/idea/cpu-and-memory-live-charts.html) ->
      Use drop-down to select process
* [Introduction to profiling | IntelliJ IDEA](https://www.jetbrains.com/help/idea/profiler-intro.html#when-is-profiling-helpful)
