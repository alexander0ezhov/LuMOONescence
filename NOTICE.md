# Notices and third-party licenses

This project is a modified fork of **Moonlight for Android** by Cameron Gutman and the Moonlight
contributors (https://github.com/moonlight-stream/moonlight-android), distributed under the
**GNU General Public License v3**. The full license text is in [LICENSE.txt](LICENSE.txt).

- Original work: Copyright (C) Cameron Gutman and Moonlight contributors.
- Modifications: Copyright (C) Alexander Ezhov and LuMOONescence contributors, 2026.

## Modifications

This fork was started on **26 October 2026**, and its changes are dated from that point. Modified
files carry the notice that they were changed — in the file itself where the change is a file that
can hold a header, and in this notice where it cannot.

The modified work does not claim authorship of the original code. Every build is made from a tag of
this repository, so the date of the sources behind a build is the date of its tag (see
[RELEASING.md](RELEASING.md)).

What the fork changes: the on-screen layout subsystem (editor, layout sets, the stream menu), touch
input modes, the built-in layouts, the fork's own name, identity and artwork, and the removal of the
old on-screen control implementation. The upstream history is kept as it is, so `git log` shows both
the original authorship and what came later.

The name "Moonlight" and its logo belong to the Moonlight project. This fork is not an official
Moonlight build and does not use the Moonlight name or logo as its own identity. "GameStream" is a
feature of NVIDIA's software, and NVIDIA is not affiliated with this project.

## Third-party components

These are the components that end up in the build, with the version the build actually resolves and
the license each one is distributed under. The complete text of every one of them is shown in the app
under **Settings → About → Open source licenses**, and is in
[app/src/main/res/raw/licenses.txt](app/src/main/res/raw/licenses.txt):

| Component | Version | License | Used for |
|---|---|---|---|
| moonlight-common-c | submodule | GNU GPL v3 | streaming protocol core |
| OpenSSL | as built by moonlight-common-c | Apache License 2.0 | TLS in the native core |
| Opus | as built by moonlight-common-c | BSD 3-Clause | audio codec |
| okhttp | 5.5.0 | Apache License 2.0 | HTTP |
| jmdns | 3.6.3 | Apache License 2.0 | host discovery |
| bcprov-jdk18on | 1.85.2 | MIT (Bouncy Castle) | certificates and crypto |
| bcpkix-jdk18on | 1.85 | MIT (Bouncy Castle) | certificates and crypto |

Bouncy Castle numbers its jars separately, so `bcprov` is at 1.85.2 while `bcpkix` is at 1.85:
1.85.2 of `bcpkix` is not published, and the two are the latest of each.
| jcodec | 0.2.5 | BSD 2-Clause | H.264 utilities |
| ShieldControllerExtensions | 1.0.1 | MIT | NVIDIA Shield controller mapping |

Apache License 2.0 requires that the notices below are kept, which the app does:

    Copyright 2019 Square, Inc.  (okhttp)
    Copyright 2004-2024 JmDNS contributors  (jmdns)

Bouncy Castle states its own license as MIT, in the jar itself (`META-INF/LICENSE.md`, "Copyright (c)
2000-2026 The Legion of the Bouncy Castle Inc."), and that is the text the app shows.

## Artwork

The name, the logo, the launcher icon and the icon set of this fork were made for this project, and
are distributed under the same GPL v3 as the rest of it. Artwork that comes from Moonlight for
Android stays under the GPL v3 as well, with the original authorship intact. No third-party
commercial artwork is used.

Two of the pictures in `docs/screenshots` are illustrations made for this project rather than
screenshots of a real game. The one screenshot of somebody else's work is of SuperTuxKart, whose
artwork is CC BY-SA 4.0, so it carries its credit: [docs/screenshots/CREDITS.md](docs/screenshots/CREDITS.md)
says what every picture in that folder is and under which licence it travels.

## Services the app talks to

By default the app uses the public infrastructure of the Moonlight project:

- `stun.moonlight-stream.org:3478` — a STUN server, used to find the external address for streaming
  over the internet;
- `android.conntest.moonlight-stream.org` — the network test run when a host is being added.

These are services run by the Moonlight project, not by this app: they are available to anyone using
a Moonlight client, and their operator sets the terms on which they may be used. This project does
not claim to grant any right to them, and it does not depend on them — both addresses are kept in one
place each, `ComputerManagerService` (STUN) and `ServerHelper` (the network test), so a build that
should use its own infrastructure only needs those two changed.

## Source code

The complete corresponding source of every published build is available in this repository, at the
tag that matches the build: https://github.com/alexander0ezhov/LuMOONescence
