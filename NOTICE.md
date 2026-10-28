# Notices and third-party licenses

This project is a modified fork of **Moonlight for Android** by Cameron Gutman and the Moonlight
contributors (https://github.com/moonlight-stream/moonlight-android), distributed under the
**GNU General Public License v3**. The full license text is in [LICENSE.txt](LICENSE.txt).

- Original work: Copyright (C) Cameron Gutman and Moonlight contributors.
- Modifications in this fork: Copyright (C) the authors of LuMOONescence.

The name "Moonlight" and its logo belong to the Moonlight project. This fork is not an official
Moonlight build and does not use the Moonlight name or logo as its own identity. "GameStream" is a
feature of NVIDIA's software, and NVIDIA is not affiliated with this project.

## Third-party components

| Component | License | Used for |
|---|---|---|
| moonlight-common-c | GPL v3 | streaming protocol core (JNI submodule) |
| okhttp | Apache License 2.0 | HTTP requests |
| jmdns | Apache License 2.0 | host discovery |
| Bouncy Castle | Bouncy Castle Licence (MIT-style) | certificates and crypto |
| JCodec | BSD 2-Clause | H.264 utilities |
| OpenSSL | Apache License 2.0 | TLS in the native core |
| Opus | BSD 3-Clause | audio codec |

Apache License 2.0 requires that the notices below are kept:

    Copyright 2019 Square, Inc.  (okhttp)
    Copyright 2004-2024 JmDNS contributors  (jmdns)

The complete license texts are shown in the app under
**Settings → About → Open source licenses**.

## Services the app talks to

By default the app uses the public infrastructure of the Moonlight project:

- `stun.moonlight-stream.org:3478` — a STUN server, used to find the external address for streaming
  over the internet;
- `android.conntest.moonlight-stream.org` — the network test run when a host is being added.

There is no legal restriction on using them: they are open public services. They are, however,
somebody else's machines, and they can be rate limited, changed or switched off without notice. Both
addresses are kept in two places — `ComputerManagerService` (STUN) and `ServerHelper` (the network
test) — so they can be pointed somewhere else without hunting through the code.

## Source code

The complete corresponding source of every published build is available in this repository, at the
tag that matches the build: https://github.com/alexander0ezhov/LuMOONescence
