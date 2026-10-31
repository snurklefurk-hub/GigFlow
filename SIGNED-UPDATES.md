# Signed in-app updates for Ride Offer Reader

This app uses the same release-channel pattern as NightRider: the Reader screen checks
GitHub Releases for higher-version signed APKs. It downloads only the exact named
`Ride-Offer-Reader.apk` asset from a `ror-v<versionCode>` release, validates the
release-body SHA-256 digest and APK package/version, then stages the APK through
Android's package installer for explicit user approval.

**The one-time signing setup is required.** GitHub Actions debug builds use temporary
Android signing keys and the separate package ID `app.rideofferreader.driver.debug`.
They cannot update the permanently signed package `app.rideofferreader.driver`,
nor can debug builds reliably update one another from different workflow runs.

## One-time release signing setup

1. Generate a permanent private keystore using a trusted local Java JDK.
   Keep the keystore and passwords in secure offline backups:

   ```sh
   keytool -genkeypair -v -keystore ride-offer-reader-release.jks -storetype PKCS12 -alias reader -keyalg RSA -keysize 3072 -validity 10000
   ```

2. Encode the file in Base64 (this is **not** encryption). On Windows PowerShell:

   ```powershell
   [Convert]::ToBase64String([System.IO.File]::ReadAllBytes("ride-offer-reader-release.jks"))
   ```

3. In the GigFlow repository's **Settings > Secrets and variables > Actions**, add
   these repository secrets (do not commit them or paste secret values in chat):

   | Secret | Value |
   | --- | --- |
   | `ROR_SIGNING_KEYSTORE_BASE64` | Base64-encoded keystore bytes |
   | `ROR_SIGNING_STORE_PASSWORD` | Keystore password |
   | `ROR_SIGNING_KEY_ALIAS` | `reader` (or the alias chosen above) |
   | `ROR_SIGNING_KEY_PASSWORD` | Private key password |

4. Merge a tested update PR into `main`. The `signed-update.yml` workflow then
   tests the source, builds a release using the stable certificate, verifies its
   signature and publishes a GitHub Release with the checksum. The workflow also
   supports **Actions > Publish signed Ride Offer Reader update > Run workflow**.
   If all four secrets are absent, publishing is safely skipped. Partial configuration
   fails the workflow.

5. **One-time debug-to-release migration:** remove the existing debug prototype and
   install the signed `Ride-Offer-Reader.apk` from the new release. These are separate
   package IDs; the debug app can alternatively remain installed alongside the signed
   app, but its data will *not* transfer automatically. Once signed release is installed,
   all later signed updates can be installed in place with settings retained.

## Normal operation

Open **Reader > About > Check for updates**. The app also checks once when Reader
opens in the signed build. If a newer release exists, tap **Download and install
update**. Android may first ask to **Allow from this source**. Return to the app
and retry, then approve the normal installation prompt.

No silent installation and no background monitoring of the update feed. Offer data
continues to remain on the device; GitHub only receives ordinary update requests.

## Testing

Before relying on this flow, confirm the first signed version installs normally,
then publish a second higher-version signed release and confirm its in-place upgrade
on a test device. Confirm no-network errors, canceled permissions, canceled installation,
invalid digest and mismatched signing certificates cannot silently install.
Losing the signing key prevents in-place updates to devices signed with that key.

The signer secrets are deliberately **not** managed by this ChatGPT integration.
