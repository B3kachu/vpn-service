# Simple OpenVPN + VLESS Importer

This Android Studio project provides two connection modes in one app:

- `OpenVPN` import and launch through the official external API of **OpenVPN for Android** (`de.blinkt.openvpn`)
- `VLESS` link save and handoff to a compatible external client on the device

The app does not implement the OpenVPN or VLESS engines itself. It acts as a clean Android front end that stores profile data, lets the user switch between protocols, and launches the right client flow.

## What the app does

- shows separate `OpenVPN` and `VLESS` sections in one interface
- imports a local `.ovpn` file from storage
- injects username and password into the OpenVPN profile as inline `<auth-user-pass>`
- stores imported profile data in app-private storage
- connects to **OpenVPN for Android** through its documented AIDL API
- requests OpenVPN API permission
- requests Android VPN permission through OpenVPN for Android
- starts the selected OpenVPN profile
- accepts `vless://` links
- parses VLESS server, port, transport, security, host, and path
- saves the VLESS profile locally
- opens the VLESS link in a compatible installed client
- shows connection and import status in the UI

## Important requirements

### OpenVPN mode

You must install **OpenVPN for Android** by Arne Schwabe on the device first.

Package name:

- `de.blinkt.openvpn`

### VLESS mode

You must install a compatible VLESS client on the device.

Examples:

- `Hiddify`
- `v2rayNG`

This project currently launches the saved `vless://` link in an external client. It does not embed `Xray-core` or a built-in VLESS engine inside the APK.

## Build and run

1. Open the `SimpleKotlinVpn` folder in Android Studio.
2. Let Gradle sync.
3. Run the app on Android 5.0+.

### OpenVPN flow

1. Install **OpenVPN for Android** on the test device.
2. Open this app.
3. Switch to the `OpenVPN` tab.
4. Press **Import .ovpn Profile** and select your profile file.
5. Enter username and password.
6. Press **Connect**.
7. Confirm API permission for OpenVPN for Android.
8. Confirm VPN permission if OpenVPN for Android asks for it.

### VLESS flow

1. Install `Hiddify` or `v2rayNG` on the device.
2. Open this app.
3. Switch to the `VLESS` tab.
4. Paste a `vless://` link.
5. Press **Save VLESS Profile** if you want to keep it in the app.
6. Press **Connect VLESS**.
7. The app will open the link in a compatible installed client.

If no compatible VLESS client is installed, the app copies the link to the clipboard and shows a message.

## Notes

- Imported `.ovpn` data is stored in app-private storage because it can contain certificates and other sensitive material.
- The actual VPN engine, routing, and system notification are handled by the external client used for the selected protocol.
- Before publishing this project, keep usernames, passwords, and live `vless://` links out of source code and test defaults.

## README на русском

Это Android Studio проект с двумя режимами подключения в одном приложении:

- `OpenVPN` через официальный внешний API приложения **OpenVPN for Android**
- `VLESS` через сохранение `vless://` ссылки и запуск во внешнем совместимом клиенте

Само приложение не содержит встроенный движок OpenVPN или VLESS. Оно выступает как Android-интерфейс для импорта профилей, хранения данных и запуска нужного клиента.

### Что умеет приложение

- показывает отдельные разделы `OpenVPN` и `VLESS`
- импортирует локальный `.ovpn` файл
- подставляет логин и пароль в OpenVPN-профиль через inline `<auth-user-pass>`
- хранит импортированные данные в приватном хранилище приложения
- подключается к **OpenVPN for Android** через его AIDL API
- запрашивает разрешение на API OpenVPN
- запрашивает системное VPN-разрешение через OpenVPN for Android
- запускает выбранный OpenVPN-профиль
