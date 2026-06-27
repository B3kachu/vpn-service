# Simple VLESS Client

This project is now a VLESS-only Android app with a built-in `VpnService` flow.

## What is already inside the app

- VLESS-only UI
- local storage for the `vless://` profile
- embedded `VpnService`
- Xray JSON config builder for VLESS links
- foreground-service connect / disconnect flow
- reflection-based bridge for `libv2ray` / `AndroidLibXrayLite`

## What is still required for a real connection

To connect directly from the app, place a compatible prebuilt `.aar` into:

- `app/libs`

Without that AAR, the app can save the profile and request VPN permission, but it cannot start the embedded VLESS core.

## Project settings

- `minSdk = 24`
- Kotlin
- Android Studio / Gradle
- OpenVPN removed from the app module

## How to build the required core AAR

The repository already contains `AndroidLibXrayLite` sources. The upstream build steps are:

1. Install JDK, Android SDK, Go, and `gomobile`
2. Run `gomobile init`
3. In `AndroidLibXrayLite`, run `go mod tidy -v`
4. Build:

```bash
gomobile bind -v -androidapi 24 -trimpath -ldflags='-s -w -buildid= -checklinkname=0' ./
```

Then copy the generated `.aar` into:

- `outputs/SimpleKotlinVpn/app/libs/`

After that, sync Gradle in Android Studio and run the app.

## README на русском

Это теперь VLESS-only версия приложения. В самом `app` модуле больше нет зависимости от OpenVPN или Hiddify.

### Что уже сделано

- полностью оставлен только `VLESS`
- добавлен собственный `VpnService`
- добавлен foreground service для подключения
- добавлен parser для `vless://` ссылки
- добавлен builder, который собирает runtime-конфиг Xray
- добавлен bridge под `libv2ray` / `AndroidLibXrayLite`
- экран уже умеет делать `Connect / Disconnect` через само приложение

### Что еще нужно для реального подключения

Нужно положить готовый `.aar` с `libv2ray` в:

- `outputs/SimpleKotlinVpn/app/libs/`

Пока этого `.aar` нет, приложение честно работает как VLESS-клиентская оболочка, но встроенный core не сможет стартовать.

### Как запустить после этого

1. Открыть `outputs/SimpleKotlinVpn` в Android Studio
2. Дождаться Gradle Sync
3. Проверить, что `.aar` лежит в `app/libs`
4. Запустить `app` на устройстве или эмуляторе с Android 7.0+
5. Вставить `vless://` ссылку
6. Нажать `Connect`
