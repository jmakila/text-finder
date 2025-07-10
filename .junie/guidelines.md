# IDFinder Project Guidelines

This document provides essential information for developers working on the IDFinder project.

## Build/Configuration Instructions

### Project Setup
- The project uses Gradle with Kotlin DSL for build configuration
- Kotlin version: 2.0.21
- Android Gradle Plugin version: 8.11.0
- Minimum SDK: 31 (Android 12)
- Target SDK: 36
- Compile SDK: 36
- JVM Target: Java 11

### Building the Project
To build the project:
1. Ensure you have Android Studio Iguana or newer installed
2. Open the project in Android Studio
3. Sync the project with Gradle files
4. Build the project using the "Build" menu or by running:
   ```
   ./gradlew build
   ```

### Dependencies
The project uses a version catalog (libs.versions.toml) for dependency management. Key dependencies include:
- Jetpack Compose (2024.09.00) for UI
- AndroidX Core KTX (1.10.1)
- AndroidX Lifecycle Runtime KTX (2.6.1)
- Material3 for design components

## Testing Information

### Test Configuration
The project has two types of tests:
1. **Unit Tests**: Located in `app/src/test/` - Run on the JVM without Android dependencies
2. **Instrumented Tests**: Located in `app/src/androidTest/` - Run on an Android device or emulator

### Running Tests

#### Unit Tests
Run all unit tests:
```
./gradlew test
```

Run a specific test class:
```
./gradlew testDebugUnitTest --tests "fi.laptech.codefinder.SimpleUtilTest"
```

#### Instrumented Tests
Run all instrumented tests (requires a connected device or emulator):
```
./gradlew connectedAndroidTest
```

### Adding New Tests

#### Unit Tests
1. Create a new Kotlin file in `app/src/test/java/fi/laptech/codefinder/`
2. Name the file with the pattern `*Test.kt` (e.g., `UtilityTest.kt`)
3. Annotate test methods with `@Test`
4. Use JUnit assertions to verify expected behavior

Example:
```kotlin
package fi.laptech.codefinder

import org.junit.Test
import org.junit.Assert.*

class UtilityTest {
    @Test
    fun testSomeFunction() {
        assertEquals(expected, actual)
    }
}
```

#### Instrumented Tests
1. Create a new Kotlin file in `app/src/androidTest/java/fi/laptech/codefinder/`
2. Annotate the class with `@RunWith(AndroidJUnit4::class)`
3. Use `InstrumentationRegistry` to access the app context
4. For Compose UI testing, use `ComposeTestRule`

Example:
```kotlin
package fi.laptech.codefinder

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("fi.laptech.codefinder", appContext.packageName)
    }
}
```

## Additional Development Information

### Code Style
- Follow Kotlin coding conventions
- Use Compose best practices for UI components
- Organize code by feature rather than by type

### Project Architecture
- The project uses Jetpack Compose for the UI layer
- Follow MVVM (Model-View-ViewModel) architecture where appropriate
- Keep UI logic in composable functions and business logic in ViewModels

### Debugging Tips
- Use Android Studio's built-in debugger for runtime issues
- For Compose UI debugging, enable the Layout Inspector
- Use `@Preview` annotations to preview composables without running the app

### Performance Considerations
- Avoid unnecessary recompositions in Compose UI
- Use `remember` and `derivedStateOf` appropriately
- Consider using Kotlin coroutines for asynchronous operations