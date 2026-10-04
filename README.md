# NextSign

NextSign is a native Android client for LibreSign, the electronic signature app for Nextcloud. It
does not prepare documents or place signature fields - that happens elsewhere (the LibreSign web
UI, or whoever sent you the request). NextSign's job is to show you what is waiting for your
signature and let you sign it with a tap, primarily through LibreSign's `clickToSign` method (no
password or code needed). There is deliberately no password-based signing fallback.

This is the Android counterpart to the [Ubuntu Touch NextSign app](https://github.com/EtherGhost/NextSign)
- a separate, independent app (different UI toolkit, different account/auth mechanism), not a
port of that codebase.

## Status

Published on [Google Play](https://play.google.com/store/apps/details?id=se.cloudsite.nextsign).
What exists so far:

- Nextcloud Single Sign-On authentication via the Nextcloud Files app, with instant switching
  between previously-approved accounts (and an avatar in the top bar) - no need to go through the
  Files app's approval flow again - plus a sign-out option when you're done with an account.
- The shared app shell: hamburger navigation, settings (theme, notifications), and an about
  screen.
- One document list showing every document you're involved in, each with its own status (ready
  to sign, partially signed, signed, or not assigned to you) - sorted to show what needs your
  signature first by default. Tap a document to see every signer's status and any custom message
  the requester left.
- A launcher icon badge - a dot or a number, depending on your device and launcher - when
  documents are waiting on your signature, kept in sync in the background alongside the
  notification options below.
- Lets you view a document before or after signing: downloads it and hands it to whichever app
  you pick via Android's share sheet, rather than an in-app PDF viewer.
- Signs a document with a tap, using LibreSign's `clickToSign` method, after a confirmation
  prompt. This is the app's core feature.
- Set up a signature by picking an image or drawing it with a finger or stylus, used
  automatically when a document needs a visible signature.
- Validates a signed document's signature and shows LibreSign's own verdict for it.
- Notifications when a document needs your signature, configured independently per account: off,
  a periodic background check (needs no extra app), or instant delivery via
  [UnifiedPush](https://unifiedpush.org/) (needs a small distributor app such as `ntfy`
  installed). The reminder can be snoozed for a duration you choose, or dismissed until the
  document is resolved.
- Available in English, Swedish, German, French, Spanish, Italian, Dutch, Danish, Norwegian
  Bokmål, Polish, and Russian, with an in-app language picker (or follow the device's system
  language automatically).

## Disclaimer

Built and maintained in spare time, by one person - not an official or supported product.

- Bug reports about the app itself are welcome, with no guaranteed response time or fix. No
  support at all for setting up or troubleshooting your own Nextcloud/LibreSign server - that's
  outside this project entirely.
- For anything involving actually signing a document, it's worth independently verifying (e.g. in
  the LibreSign web UI) that the signature applied correctly before relying on it for anything
  that matters.
- Not affiliated with, endorsed by, or supported by Nextcloud GmbH, the Nextcloud project, or the
  LibreSign project.
- Provided under the MIT license (see [`LICENSE`](LICENSE)) - no warranty of any kind.

## Technology

Kotlin + Jetpack Compose, using `Android-SingleSignOn` + Retrofit for authenticated calls to
LibreSign's REST API on a self-hosted Nextcloud instance.

## License

MIT - see [`LICENSE`](LICENSE).
