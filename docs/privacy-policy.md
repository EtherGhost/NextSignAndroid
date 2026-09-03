---
title: NextSign Privacy Policy
---

# NextSign for Android — Privacy Policy

**Effective date:** September 4, 2026

NextSign is a hobby project built and maintained by one person in their spare time (see the
[README](https://github.com/EtherGhost/NextSignAndroid#readme) for the full disclaimer). This
page explains what data the app touches, in plain language.

## Summary

NextSign does not collect, store, or transmit your personal data to the developer or to any
third party. The app is a client for LibreSign, the e-signature app for Nextcloud: it connects
directly to **your own** Nextcloud server, using an account you choose, and every document,
signature, and account credential stays between your device and that server. There is no backend
run by the developer, no analytics, no advertising, and no crash-reporting service.

## Account and authentication

NextSign signs you in through the official **Nextcloud Files app**, using Nextcloud's Single
Sign-On mechanism. NextSign never sees or stores your Nextcloud username or password — the Files
app handles authentication and hands NextSign a token scoped to the account you picked.

The app requests the `READ_CONTACTS` Android permission because Nextcloud's account-picker system
is built on Android's standard account manager, which requires this permission to list the
Nextcloud accounts you've already added on your device. NextSign does not read, use, or transmit
your contacts.

## Data sent over the network

All network requests NextSign makes go directly to the Nextcloud server tied to the account you
selected, over HTTPS, using LibreSign's own REST API (listing documents waiting for your
signature, signing a document, downloading a document or a signature image, etc.). The developer
never receives a copy of this traffic — it is exactly the same as using the LibreSign web
interface, just from a native app.

## Data stored on your device

- **Theme and notification preferences** — settings saved locally via Android's SharedPreferences
  (light/dark/follow-system theme, and your chosen notification mode).
- **Account list** — the names of Nextcloud accounts you've previously approved in NextSign, kept
  locally so you can switch between them instantly. No passwords or tokens are stored here — see
  "Account and authentication" above.
- **List of documents already seen** — the IDs of documents NextSign has already shown you or
  notified you about, kept locally so the background check and push notifications don't notify
  you twice for the same document.
- **Temporary cached files** — when you open or share a document, or view your signature preview,
  NextSign downloads it to the app's private cache folder so it can hand it to another app (e.g.
  a PDF viewer) or display it. These files stay on your device, are not sent anywhere else by
  NextSign, and are cleared automatically by Android (and always removed when the app is
  uninstalled).

Nothing above is ever transmitted to the developer.

## Notifications

If you turn on notifications (Settings screen), NextSign can tell you when a document needs your
signature in two ways, chosen by you:

- **Background check** — the app periodically asks your Nextcloud server, in the background,
  whether anything new needs your signature. No third party is involved.
- **Instant** — uses [UnifiedPush](https://unifiedpush.org/), an open push-notification standard,
  instead of polling. This requires installing a separate "distributor" app of your choice (such
  as `ntfy`) — NextSign does not include or bundle one. When you enable this, NextSign asks your
  Nextcloud server to send future notifications to an endpoint address provided by that
  distributor app. Your Nextcloud server then delivers notifications to that endpoint — depending
  on which distributor you installed and how you've configured it, this may involve a third-party
  relay service (for example, the public `ntfy.sh` service, if you use the default `ntfy` app).
  Notification content is encrypted (Web Push encryption) so only your device can read it, but the
  fact that a notification was sent, and to which endpoint, passes through that relay. Read the
  distributor app's own privacy policy if this matters to you. Choosing "Off" or "Background
  check" avoids this entirely.

## Third parties

NextSign includes no analytics, advertising, or crash-reporting SDKs, and does not itself share
data with any third party. The only third-party involvement at all is the optional "Instant"
notification path described above, and only if you choose it and install a distributor app.
Otherwise, the only network communication is the direct connection described earlier, between
your device and the Nextcloud server you configured.

## Children's privacy

NextSign does not knowingly collect data from anyone, regardless of age, for the reasons
described above.

## Changes to this policy

If this policy changes, the update will be reflected here with a new effective date.

## Contact

This is a solo project with no dedicated support channel. Questions or concerns can be raised via
[GitHub Issues](https://github.com/EtherGhost/NextSignAndroid/issues).
