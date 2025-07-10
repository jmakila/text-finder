# Text Finder

An Android application that allows users to search for text in real-time using the device camera with ML Kit text recognition and visual highlighting.

## Features

- **Two-Screen Interface**: Simple text input screen and camera preview screen
- **Real-time Text Recognition**: Uses Google ML Kit for accurate text detection
- **Visual Text Highlighting**: Highlights found text with overlay boxes on camera feed
- **Bilingual Support**: Available in English and Finnish
- **Portrait Mode Only**: Optimized for portrait orientation
- **Keep Screen Awake**: Device stays awake during camera usage
- **Intuitive Navigation**: Easy back navigation between screens
- **Modern UI**: Built with Jetpack Compose and Material 3 design

## Screenshots

The app consists of two main screens:
1. **Input Screen**: Enter the text you want to find
2. **Camera Screen**: Real-time camera preview with text highlighting and search overlay

## Requirements

- **Android Version**: Android 12 (API level 31) or higher
- **Camera**: Device must have a camera
- **Permissions**: Camera permission required

## Installation

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 36
- Kotlin support
- Device or emulator with camera support

### Build Instructions

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd IDFinder
   ```

2. Open the project in Android Studio

3. Sync the project with Gradle files

4. Build and run the application:
   - Connect an Android device or start an emulator
   - Click "Run" in Android Studio or use `./gradlew installDebug`

## Usage

1. **Launch the App**: Open Text Finder on your device
2. **Enter Search Text**: Type the text you want to find in the input field
3. **Start Search**: Tap the "Search" button to proceed to camera screen
4. **Point Camera**: Aim your camera at text-containing surfaces
5. **View Results**: Found text will be highlighted with colored overlay boxes
6. **Navigate Back**: Use the back button to return to the input screen

## Project Structure

```
app/
├── src/main/
│   ├── java/fi/laptech/textfinder/
│   │   ├── MainActivity.kt          # Main activity with Compose UI
│   │   └── ui/theme/               # App theming
│   ├── res/
│   │   ├── values/strings.xml      # English strings
│   │   ├── values-fi/strings.xml   # Finnish strings
│   │   └── ...                     # Other resources
│   └── AndroidManifest.xml         # App configuration
├── build.gradle.kts                # App-level build configuration
└── ...
```

## Key Components

### MainActivity.kt
- **InputScreen**: Text input interface with navigation
- **CameraScreen**: Camera preview with ML Kit integration
- **CameraPreview**: Camera functionality and text overlay rendering
- **TextAnalyzer**: ML Kit text recognition processing
- **Navigation**: Jetpack Navigation Compose implementation

### Dependencies

- **Jetpack Compose**: Modern Android UI toolkit
- **CameraX**: Camera functionality
- **ML Kit Text Recognition**: Google's text recognition API
- **Navigation Compose**: Screen navigation
- **Material 3**: Modern Material Design components

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: Single Activity with Compose Navigation
- **Camera**: CameraX with ImageAnalysis
- **Text Recognition**: Google ML Kit
- **Minimum SDK**: 31 (Android 12)
- **Target SDK**: 36
- **Orientation**: Portrait only

## Permissions

The app requires the following permissions:
- `CAMERA`: For camera access and text recognition

## Localization

The app supports:
- **English** (default)
- **Finnish** (fi)

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Development Notes

- The app keeps the screen awake during camera usage
- Portrait orientation is enforced in the manifest
- Text recognition runs in real-time with efficient processing
- UI follows Material 3 design guidelines
- Proper permission handling with user-friendly messages

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support, please open an issue in the repository or contact the development team.

---

**Built with ❤️ using Kotlin and Jetpack Compose**