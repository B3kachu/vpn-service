# Simple OpenVPN Importer

This Android Studio project imports a local `.ovpn` file, injects username/password as inline `<auth-user-pass>`, and then adds/starts the profile through the official external API of **OpenVPN for Android** (`de.blinkt.openvpn`).

## What changed

The app no longer tries to implement OpenVPN itself with a raw `VpnService` tunnel.

Instead, it:

- lets the user pick an `.ovpn` file from storage
- stores the config in the app private storage
- connects to **OpenVPN for Android** over its AIDL API
- requests OpenVPN API permission
- requests Android VPN permission through OpenVPN for Android
- imports the profile and starts it
- shows live status updates returned by the OpenVPN app

## Important requirement

You must install **OpenVPN for Android** by Arne Schwabe on the device first.

Package name:

- `de.blinkt.openvpn`

This project uses its documented external API instead of embedding the full OpenVPN engine.

## Build and run

1. Open the `SimpleKotlinVpn` folder in Android Studio.
2. Let Gradle sync.
3. Install **OpenVPN for Android** on the test device.
4. Run this app on Android 5.0+.
5. Press **Import .ovpn Profile** and select your profile file.
6. Confirm API permission for OpenVPN for Android.
7. Press **Connect**.
8. Confirm VPN permission if OpenVPN for Android asks for it.

## Notes

- The app defaults the username/password fields to the values that were requested during this session.
- The imported `.ovpn` is stored in the app private storage because it contains sensitive material such as certificates and possibly private keys.
- This app controls OpenVPN for Android, so the actual VPN engine, notification, and routing are handled by that application.
