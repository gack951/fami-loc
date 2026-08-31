# FamiLoc Android PoC

FCM の high-priority data message を受けた時だけ、15 秒以内で精度 25 m 以下を目標に現在地を取得し、HTTPS API へ送信して Foreground Service を停止する Phase 1 PoC です。

## ローカル設定

1. Firebase Android app `jp.familoc` を作り、`google-services.json` を `app/` に置く。
2. `local.properties` に次を設定する。

```properties
sdk.dir=/path/to/Android/Sdk
FAMILOC_API_BASE_URL=https://example.test
FAMILOC_DEVICE_TOKEN=replace-with-revocable-device-token
```

API は以下を 2xx で応答させます。

- `GET /api/location-requests/{request_id}`: 対象端末向けで未期限切れなら 200
- `POST /api/location-requests/{request_id}/location`
- `POST /api/location-requests/{request_id}/status`
- `POST /api/devices/fcm-token`

FCM は notification message ではなく high-priority data message とし、`request_id` を含めます。位置情報や token は Logcat に出力しません。

GitHub Actions には実機用の `GOOGLE_SERVICES_JSON`, `FAMILOC_API_BASE_URL`, `FAMILOC_DEVICE_TOKEN` を Secrets として設定します。未設定時の CI はダミー Firebase 設定でコンパイルだけを検証します。
