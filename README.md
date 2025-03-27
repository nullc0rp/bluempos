# 🧠 BlueMPOS CTF – BLE + NFC Mobile POS Challenge

A Capture The Flag (CTF) challenge designed to teach Bluetooth Low Energy (BLE) and NFC exploitation, all inside a custom Android app that mimics a vulnerable mobile POS terminal.

---

## 🛠 Features

- BLE GATT service with vulnerable read/write characteristics
- Flag hidden behind a custom BLE command (`SEND_FLAG_2`)
- Price input keypad and simulated transaction flow
- NFC card detection (IsoDep)
- Challenge-ready for in-person or remote CTFs

---

## 🎯 Goals for Players

1. 🔍 Discover the BLE GATT service and characteristic
2. 🧼 Read the characteristic value – get `LOCKED`
3. ✍️ Send the correct command (`SEND_FLAG_2`) via BLE write
4. 🎉 Receive flag `FLAG{ble-basic-read}`
5. (Optional) Tap a card to activate a future NFC-based flag

---

## 🧪 Tools Recommended

- [nRF Connect (Android/iOS)](https://www.nordicsemi.com/Products/Development-tools/nRF-Connect-for-mobile)
- `gatttool` (Linux command-line BLE client)
- A compatible NFC tag or credit card (e.g. IsoDep or MIFARE)

---

## 📦 Build the App

### Option 1: 🧠 Build using Android Studio (recommended)

1. Clone the repo:
   ```bash
   git clone https://github.com/nullc0rp/bluempos.git
   cd bluempos
   ```

2. Open the project in **Android Studio**.

3. Let it sync Gradle (accept prompts, install SDKs if needed).

4. Run the app on a connected device via USB or Wi-Fi:
   - Select device
   - Click ▶️ “Run”

5. Or build a release APK:
   - Go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**
   - The APK will be located in:  
     `app/build/outputs/apk/debug/app-debug.apk`

### Option 2: ⚙️ Build from CLI (for automation or scripting)

```bash
./gradlew assembleDebug
```

APK will be located at:

```
app/build/outputs/apk/debug/app-debug.apk
```

You can install it on your device using:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

> ✅ Make sure you have `adb` and `android-tools` installed on Arch:  
```bash
sudo pacman -S android-tools
```

---

## 📡 Using gatttool to capture the flag

```bash
sudo pacman -S bluez bluez-utils

sudo gatttool -I
[                   ]# connect XX:XX:XX:XX:XX:XX
[                   ]# char-write-req 0x0025 53454E445F464C41475F32
[                   ]# char-read-hnd 0x0025
# Should return FLAG{ble-basic-read}
```

(Replace `0x0025` with your actual characteristic handle)

---

## 🧠 Next Steps

- [ ] Add flag for NFC APDU
- [ ] Add SPP (Bluetooth Classic) command handler
- [ ] Add BLE notification-based flag
- [ ] Add real mPOS-style UI polish

---

© 2025 nullc0rp // CTF-safe, open source
