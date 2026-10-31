# Ride Offer Reader

A minimal, local-first Android companion that reads and evaluates **Uber Driver** and **Lyft Driver** ride offers displayed on screen.

This project is a focused derivative of [GigFlow](https://github.com/w0wzahh/GigFlow). It retains the upstream MIT license and copyright notice in [LICENSE](LICENSE).

## MVP scope

- Read Uber and Lyft offer text through Android Accessibility Services.
- Extract payout, trip distance, and estimated duration from the currently visible offer.
- Score offers against adjustable minimum $/mile, $/hour, payout, and maximum-distance thresholds.
- Show a small read-only verdict overlay.
- Keep diagnostics and offer history locally on the device.

## Explicitly out of scope

- No automatic or manual accept/decline.
- No screen taps, swipes, gesture dispatch, or control of Uber/Lyft.
- No GPS mileage tracking, earnings capture, subscriptions, account login, or remote telemetry.
- No delivery, courier, or other gig-app integrations.

If an offer cannot be read with confidence, the reader records a diagnostic rather than making up a result.

## Privacy

Offer text and diagnostics stay on-device. The MVP declares no location or notification permissions. Only the signed-release updater uses internet access and the user-approved Android package installer.

## In-app updates

The signed Ride Offer Reader release uses **Reader > About > Check for updates** to download and verify a newer signed release, then asks Android to install it without uninstalling the app. First-time release signing configuration is described in [SIGNED-UPDATES.md](SIGNED-UPDATES.md). CI-produced debug builds use different package IDs and temporary debug keys, and cannot join the signed update channel in place.

## Build

Android Studio with JDK 17+ and Android SDK platform 36 is required. This repository currently does not include a Gradle wrapper.

```bash
cd android
gradle :app:assembleDebug
```

## License

MIT. See [LICENSE](LICENSE).
