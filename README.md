# Guardian Safety App

Guardian is a personal emergency safety application for Android that allows users to quickly trigger an SOS alert to a predefined contact. When activated, the app sends an SMS containing the user's live location and custom emergency message, while simultaneously recording audio, sounding an alarm, and turning on the flashlight.

## Features
- **One-Tap Emergency Alert**: Rapidly trigger the panic protocol.
- **Location Sharing**: Automatically fetches and sends high-accuracy GPS coordinates via SMS.
- **Audio Recording**: Starts recording audio locally as soon as the emergency is triggered.
- **Siren & Flashlight**: Optional alarms and strobe light for immediate attention in critical situations.
- **Session Management**: Automatically keeps the user logged in and remembers their emergency contact settings.

---

## Prerequisites

Before setting up the project, ensure you have the following installed:
- **Java Development Kit (JDK) 17** or higher.
- **Android Studio** (Flamingo or later recommended).
- **Git** (for version control).

---

## 🛠 Android SDK Setup

Whether you are on Windows or Linux, you will need the Android SDK. Android Studio installs this automatically, but if you want to set it up manually or run command-line builds:

1. **Download Android Studio**: Get it from the [official Android Developer website](https://developer.android.com/studio).
2. **Install the SDK**: During the initial Android Studio setup, make sure the **Android SDK**, **Android SDK Command-line Tools**, and **Android SDK Build-Tools** are checked.
3. **Set Environment Variables**: 
   You need to define the `ANDROID_HOME` environment variable pointing to your SDK directory.
   - **Linux Default Path**: `/home/<Your-Username>/Android/Sdk`
   - **Windows Default Path**: `C:\Users\<Your-Username>\AppData\Local\Android\Sdk`

---

## 🚀 Setup Instructions



### For Linux

1. **Clone the Repository**:
   Open your terminal and run:
   ```bash
   git clone https://github.com/your-usernameSOS-alerter/
   cd SOS-alerter
   ```
2. **Set Environment Variables**:
   Open your `~/.bashrc` or `~/.zshrc` file and add the following lines at the end:
   ```bash
   export ANDROID_HOME=$HOME/Android/Sdk
   export PATH=$PATH:$ANDROID_HOME/emulator
   export PATH=$PATH:$ANDROID_HOME/tools
   export PATH=$PATH:$ANDROID_HOME/tools/bin
   export PATH=$PATH:$ANDROID_HOME/platform-tools
   ```
   Apply the changes by running:
   ```bash
   source ~/.bashrc  # or source ~/.zshrc
   ```
3. **Configure Local Properties**:
   Ensure you have a `local.properties` file in the root of the project pointing to your SDK:
   ```properties
   sdk.dir=/home/<Your-Username>/Android/Sdk
   ```
4. **Open in Android Studio**:
   - Launch Android Studio from your terminal (`studio.sh`) or application menu.
   - Click on **Open** and select the `saftyapp` folder.


### For Windows

1. **Clone the Repository**:
   Open Command Prompt or PowerShell and run:
   ```bash
   git clone https://github.com/your-username/saftyapp.git
   cd saftyapp
   ```
2. **Open in Android Studio**:
   - Open Android Studio.
   - Click on **Open**.
   - Navigate to the cloned `saftyapp` directory and select it.
3. **Environment Variables**:
   - Press `Win + S`, search for "Environment Variables".
   - Under User Variables, add `ANDROID_HOME` with the path to your Android SDK.
   - Edit the `Path` variable and add `%ANDROID_HOME%\platform-tools`.
4. **Sync Gradle**: Android Studio will automatically sync the Gradle files. If it fails, click **File > Sync Project with Gradle Files**.

---


## 🏃 Running the App

### Using Android Studio
1. Connect a physical Android device via USB (enable USB Debugging in Developer Options) or start an Android Virtual Device (AVD) from Android Studio.
2. In Android Studio, click the green **Run** button (Shift + F10).
3. Accept the necessary runtime permissions (Camera, Location, Microphone, SMS) when prompted on the device.

### Using Terminal
You can also build and install the app directly from your terminal using Gradle:

1. Connect a physical Android device or start an emulator.
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Install the debug APK on your connected device:
   ```bash
   ./gradlew installDebug
   ```

## Note on GitHub
This repository contains a `.gitignore` file configured for Android. Automatically generated build files, `.idea` configuration files, and `local.properties` (which contains your local SDK path) are intentionally excluded from version control to prevent path conflicts across different computers.
