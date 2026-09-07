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

Early release, published on Google Play as a closed test. Bugs and rough edges are expected.
What exists so far:

- Nextcloud Single Sign-On authentication via the Nextcloud Files app, with instant switching
  between previously-approved accounts (and an avatar in the top bar) - no need to go through the
  Files app's approval flow again - plus a sign-out option when you're done with an account.
- The shared app shell: hamburger navigation, settings (theme, notifications), and an about
  screen.
- One document list showing every document you're involved in, each with its own status (ready
  to sign, partially signed, signed) - tap a document to see every signer's status and any custom
  message the requester left.
- Lets you view a document before or after signing: downloads it and hands it to whichever app
  you pick via Android's share sheet, rather than an in-app PDF viewer.
- Signs a document with a tap, using LibreSign's `clickToSign` method, after a confirmation
  prompt. This is the app's core feature.
- Set up a signature by picking an image or drawing it with a finger or stylus, used
  automatically when a document needs a visible signature.
- Validates a signed document's signature and shows LibreSign's own verdict for it.
- Notifications when a document needs your signature, configurable in Settings: off, a periodic
  background check (needs no extra app), or instant delivery via
  [UnifiedPush](https://unifiedpush.org/) (needs a small distributor app such as `ntfy`
  installed).
- Available in English and Swedish, following the device's system language automatically.

## Disclaimer

This is a hobby project, built and maintained in spare time - not an official or supported
product.

- **No support is offered, and this is a solo project, not a collaborative one.** Bug reports are
  welcome, but there's no guaranteed response time and no promise any given one gets fixed. The
  source is here to be read and forked, not to gather contributors.
- **Use it at your own risk**, especially anything involving actually signing a document. Verify
  independently (e.g. in the LibreSign web UI) that a signature was applied correctly before
  relying on it for anything that matters.
- **Not affiliated with, endorsed by, or supported by Nextcloud GmbH, the Nextcloud project, or
  the LibreSign project** in any way.
- Provided under the MIT license (see [`LICENSE`](LICENSE)): no warranty of any kind, used
  entirely at your own risk.

## Technology

Kotlin + Jetpack Compose, using `Android-SingleSignOn` + Retrofit for authenticated calls to
LibreSign's REST API on a self-hosted Nextcloud instance.

## License

MIT - see [`LICENSE`](LICENSE).
