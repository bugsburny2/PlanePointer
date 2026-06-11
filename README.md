# PointToPlane (WearOS Plane Spotter App)

PointToPlane is a premium WearOS application that lets you spot and identify commercial flights in real-time simply by pointing your wrist/arm at them in the sky. It is specifically optimized for circular smartwatches (e.g., OnePlus Watch 3) and includes a live compass-aligned radar screen.

---

## Key Features

1. **Point-to-Spot Scanning (Tab 1)**:
   * Raise your arm pointing at a flight (requires $10^\circ$ elevation or higher).
   * **Auto-scan** triggers after holding your arm steady for 2 seconds (debounce protection).
   * Queries real-time flight details (Callsign, Aircraft Type, Altitude, Speed, Airline, Origin, and Destination) via ADSB APIs.

2. **3D Vector Calibration Wizard**:
   * Compares the watch's $3\times3$ rotation matrix when pointing straight up (zenith) to eliminate discrepancies caused by how the watch is oriented on your wrist.
   * Features a hands-free **5-second countdown** and **vibration haptic feedback** to notify you when calibration is complete.

3. **Interactive Track-Up Radar Map (Tab 2)**:
   * Slide horizontally to view nearby aircraft.
   * Compass-aligned **Track-Up display** that rotates dynamically as you turn.
   * **Clickable range toggle**: Tap the screen to switch the radar radius between **10km, 30km, and 50km**.
   * **Flight Number Labels**: Draws the aircraft callsign/flight number directly next to the active radar dots on the map.

4. **Circular Bezel Adjustments**:
   * All UI elements (settings gear icon, pager indicator dots) are padded safely to prevent screen clipping on round watch faces.

5. **Battery & Sensor Preservation**:
   * Fully unregisters native rotation/compass sensors and disables location updates whenever the watch goes to sleep, screen dims (`onPause`), or the app exits (`onDestroy`).

---

## Compilation & Installation Instructions

### 1. Prerequisites
* **Android SDK**: Installed and available (e.g., `C:\Users\lenovo\AppData\Local\Android\Sdk`).
* **ADB**: Platform-tools installed and added to your environment path.

### 2. Build the Debug APK
Compile the project using the Gradle wrapper in the project root:
```bash
# Windows
.\gradlew assembleDebug --no-configuration-cache

# Linux / macOS
./gradlew assembleDebug --no-configuration-cache
```
The output APK is generated at:  
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Deploy to the Watch
Ensure USB debugging (or Wireless debugging) is enabled on your WearOS device, then run:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
*Note: If you get an `unauthorized` device error, wake up your watch screen and accept the "Allow USB debugging?" authorization prompt.*

### 4. Launch the App
To start the app directly on the watch from the command line:
```bash
adb shell am start -n com.example.pointtoplane/com.example.pointtoplane.MainActivity
```
