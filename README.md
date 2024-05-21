## End-to-End Stress Testing with Kotlin Multiplatform

This is an event-based example application. It features collaborative editing of text lines with desktop (JVM) and browser (JS) frontends.

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

#### Test With Fixed Frontend Count

* `./gradlew --no-build-cache --no-configuration-cache -p application/integration-test -D application.test.fixedFrontendCount=4 cleanJvmTest jvmTest --tests E2EUpdateRejectionTests`
* `./gradlew --no-build-cache --no-configuration-cache -p application/integration-test -D application.test.fixedFrontendCount=4 -D application.test.debugTrace.loggingInsteadEnabled=true cleanJvmTest jvmTest --tests E2EUpdateRejectionTests`

#### Test With Intentional Defect

* `./gradlew --no-build-cache --no-configuration-cache -p application/integration-test -D application.test.defectCountdown=2 -D application.test.fixedFrontendCount=4 cleanJvmTest jvmTest --tests E2EUpdateRejectionTests`

#### Test With Intentional Defect: Logging instead of DebugTrace

* `./gradlew --no-build-cache --no-configuration-cache -p application/integration-test -D application.test.defectCountdown=2 -D application.test.fixedFrontendCount=4 -D application.test.debugTrace.loggingInsteadEnabled=true -Dlogback.configurationFile=/Users/oliver/IdeaProjects/kmp-e2e-stress-testing/example/logback-debug-configuration.xml cleanJvmTest jvmTest --tests E2EUpdateRejectionTests`
