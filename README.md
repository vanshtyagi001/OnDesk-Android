# 📱 OnDesk

> A high-performance Android LAN remote control and screen sharing application built with modern Android technologies.  
> Stream your Android screen in real-time and remotely control another Android device over the same Wi-Fi network.

---

## ✨ Features

### 📺 Real-Time Screen Sharing
- Capture and stream Android screens with low latency
- Powered by Android `MediaProjection` and `MediaCodec`
- Smooth H.264 (AVC) video encoding and decoding

### 🎮 Full Remote Control
Remotely perform:
- Taps
- Swipes
- Multi-touch gestures

Uses Android `AccessibilityService` for gesture injection.

### 🔍 Automatic Device Discovery
- Automatically detect nearby hosts on the same LAN
- Uses Android **Network Service Discovery (NSD)**

### ⚙️ Adjustable Streaming Quality
Customize:
- Resolution
- Bitrate
- FPS (Frames Per Second)

### 🔄 Dual Mode Support
- **Host Mode** → Share and control this device
- **Client Mode** → View and control another device

### 🎨 Modern UI
- Built entirely with **Jetpack Compose**
- Clean, responsive, and modern interface

---

# 🏗️ Architecture Overview

## 1️⃣ Host Side — Screen Capture & Streaming

The host device:
- Captures the screen using `MediaProjection`
- Encodes frames using `MediaCodec`
- Streams video frames over TCP sockets

### Core Components
- `MediaProjection`
- `MediaCodec`
- `VirtualDisplay`
- TCP Socket Streaming

---

## 2️⃣ Host Side — Remote Gesture Injection

The host device runs an `AccessibilityService` that:
- Receives touch events from the client
- Injects gestures into the Android system

### Supported Gestures
- Single Tap
- Long Press
- Swipe
- Multi-touch

---

## 3️⃣ Client Side — Video Playback & Input

The client device:
- Decodes incoming H.264 video stream
- Displays the stream using Jetpack Compose
- Captures touch interactions on the stream surface
- Sends touch data back to the host

### Main Components
- `MediaCodec` Decoder
- Compose UI
- Gesture Interception Layer

---

## 4️⃣ Networking Layer

### TCP Socket Communication
Handles:
- Video stream transmission
- Touch control packets
- Connection management

### Network Service Discovery (NSD)
Automatically:
- Registers host devices
- Discovers available remote sessions

---

# 📂 Project Structure

```bash
OnDesk/
│
├── app/
│   ├── ui/                # Compose UI screens & components
│   ├── networking/        # Socket & NSD logic
│   ├── streaming/         # MediaProjection & MediaCodec
│   ├── accessibility/     # Gesture injection service
│   ├── models/            # Data models
│   └── utils/             # Utility/helper classes
│
├── gradle/
└── README.md
```

---

# 🛠️ Tech Stack

| Category | Technology |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Concurrency | Kotlin Coroutines |
| Networking | Java Sockets |
| Discovery | Android NSD |
| Streaming | MediaProjection API |
| Video Codec | MediaCodec (H.264/AVC) |
| Serialization | Gson |
| Navigation | Compose Navigation |

---

# 📋 Requirements

## Devices
- Two Android devices
- Connected to the same Wi-Fi network

## Host Device Permissions
The host device must:
- Grant **Screen Recording** permission
- Enable the **OnDesk Accessibility Service**

---

# 🚀 Getting Started

## 1️⃣ Clone the Repository

```bash
git clone https://github.com/yourusername/OnDesk.git
```

---

## 2️⃣ Open in Android Studio

- Open Android Studio
- Select **Open Project**
- Choose the cloned repository

---

## 3️⃣ Build the Project

```bash
./gradlew assembleDebug
```

Or simply use:

```text
Build → Make Project
```

---

# 📱 Usage Guide

## 🖥️ Host Mode

1. Launch the app
2. Select **Host Mode**
3. Enable the Accessibility Service
4. Grant Screen Recording permission
5. Start sharing

---

## 🎮 Client Mode

1. Launch the app
2. Select **Client Mode**
3. Wait for device discovery
4. Choose a host device
5. Connect and control remotely

---

# 🔒 Permissions Used

| Permission | Purpose |
|---|---|
| MediaProjection | Screen capture |
| AccessibilityService | Gesture injection |
| INTERNET | Socket communication |
| ACCESS_WIFI_STATE | Network detection |
| CHANGE_WIFI_MULTICAST_STATE | NSD discovery |

---

# ⚡ Performance Tips

- Lower resolutions improve latency
- Higher bitrates improve video quality
- Stable Wi-Fi improves responsiveness
- Recommended: **5GHz Wi-Fi network**

---

# 🧠 Roadmap

Planned future improvements:

- 🔐 End-to-end encryption
- 🌍 Internet/WAN support
- 📁 File transfer
- 🔊 Audio streaming
- 🎥 Session recording
- 🖱️ Keyboard & mouse emulation
- 🧩 Multi-device management

---

# 🤝 Contributing

Contributions are welcome!

## Steps

1. Fork the repository

2. Create a feature branch

```bash
git checkout -b feature/amazing-feature
```

3. Commit your changes

```bash
git commit -m "Add amazing feature"
```

4. Push to GitHub

```bash
git push origin feature/amazing-feature
```

5. Open a Pull Request

---

# 📄 License

This project is licensed under the MIT License.

```text
MIT License © 2026 OnDesk
```

---

# ⭐ Support

If you like this project:

- Give it a ⭐ on GitHub
- Share it with others
- Contribute improvements

---

# 👨‍💻 About OnDesk

**OnDesk** is designed to provide a lightweight, fast, and modern Android remote desktop experience entirely over a local network using native Android APIs and modern Kotlin development practices.