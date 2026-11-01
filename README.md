# Moonbeam by NightRider

**Cut through the noise. See what it's worth.**

Moonbeam is a free, local-first Android companion from the NightRider ecosystem. It reads and evaluates **Uber Driver** and **Lyft Driver** ride offers displayed on screen, without ever deciding or acting for the driver.

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

## Confirmed driver-app identifiers

Moonbeam's Accessibility Service must monitor the **actual Android package
IDs**, not the display names: Uber Driver is `com.ubercab.driver`, and
Lyft Driver is `com.lyft.android.driver`. These values must stay synchronized
between `res/xml/accessibility_service_config.xml`, the watched-app UI,
service heartbeat, and offer parser. The previous placeholder package names
prevented Android from delivering offer-screen events.

Ordinary live offers are tested against labels from field screenshots:
Uber's `+$1.75 included` is already inside the advertised payout, and
Lyft's estimated `$26.08/hr` is not a second payout. The reader independently
calculates total pickup + trip miles, minutes, $/mile and $/hour, and rejects
conflicting or incomplete details rather than guessing. Screenshots do **not**
prove accessibility text visibility; on-device diagnostics are still needed
to verify the actual accessibility tree on different devices and app versions.

## Keeping Moonbeam active in Uber and Lyft

Moonbeam's **Android Accessibility service** is system-managed and independent
of its own app screen. You should be able to switch to Uber or Lyft without
opening Moonbeam first or leaving it in the foreground.

For an app-switch problem, open **Moonbeam > Reader** after the switch and read
the service status and **Last driver-app signal**. An enabled accessibility
permission is not proof the service process is currently connected; the Reader
now distinguishes these states and reports the time/type of the most recent
local scanner error. No offer text or crash messages are uploaded.

On devices that restrict background processes, check Android **Settings >
Apps > Moonbeam > Battery / App battery usage** and allow unrestricted
background activity if the device offers that choice. Also verify the Moonbeam
service remains enabled under **Settings > Accessibility**. Actual settings
names vary by device. A service disabled by the operating system cannot be
silently re-enabled by Moonbeam.

**Field verification:** enable Monitoring and the Moonbeam accessibility
service, open Uber then Lyft, return to Reader, and confirm it reports recent
driver-app signals. Repeat after several minutes away from Moonbeam. If it
reports a disconnection or scan error, use that status when filing a bug report.

## In-app updates

The signed Moonbeam release uses **Reader > About > Check for updates** to download and verify a newer signed release, then asks Android to install it without uninstalling the app. First-time release signing configuration is described in [SIGNED-UPDATES.md](SIGNED-UPDATES.md). CI-produced debug builds use different package IDs and temporary debug keys, and cannot join the signed update channel in place.

## Brand and continuity

Moonbeam's midnight-blue, orange-to-green crescent and cloud-cutting moonlight signal the NightRider mission: higher standards and independent, informed drivers. The **Android package ID remains `app.rideofferreader.driver`**, the signing key remains unchanged, and the update feed still uses `ror-v...` releases with the original `Ride-Offer-Reader.apk` asset name. This is deliberate: installed users update in place without losing preferences or diagnostics. The Moonbeam name is a pilot identity pending trademark clearance before wider external promotion.

*Made to illuminate the bullshit.*

## Build

Android Studio with JDK 17+ and Android SDK platform 36 is required. This repository currently does not include a Gradle wrapper.

```bash
cd android
gradle :app:assembleDebug
```

## License

MIT. See [LICENSE](LICENSE).
