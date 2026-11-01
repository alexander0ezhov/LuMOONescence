# Releasing a build

The GPL v3 asks that the source distributed with a build is the source the build was made from, and
the app tells people that its source is published at the tag of the build. This is the procedure that
keeps that true.

## Before building

1. **Everything is committed.** `git status` is empty apart from files that are deliberately ignored.
2. **The submodules are at the commits the build uses.** The native streaming core and the
   controller extensions come from submodules, so their commits are part of the source:

   ```powershell
   git submodule update --init --recursive
   git submodule status
   ```

   If a submodule is at a commit that is not what the release should use, commit the change in
   `.gitmodules` / the gitlink first, so the tag records it.

3. **The version is bumped** in `app/build.gradle` (`versionName`, `versionCode`) — the tag, the app
   and the release page should all say the same thing.

## Building

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat clean :app:assembleNonRootRelease :app:assembleRootRelease
```

The APKs land in `app/build/outputs/apk/nonRoot/release/` and `app/build/outputs/apk/root/release/`.
The build must be made **from the tagged commit**, not from a working tree that has uncommitted
changes.

## Tagging and publishing

1. Tag the commit the build came from:

   ```powershell
   git tag -a v1.0 -m "LuMOONescence 1.0"
   git push origin v1.0
   ```

2. Attach the APKs to the release page for that tag, and say in the release notes which flavour is
   which (`nonRoot` for most devices, `root` only for Android 7 and older devices that need the rooted
   input driver).

3. If the tag has to be moved after a build was published, publish a new build rather than moving the
   tag: a tag that no longer matches a released APK is exactly what the license is meant to prevent.

## The license screen

The text shown under **Settings → About → Open source licenses** lives in
`app/src/main/res/raw/licenses.txt`. It has to list every component that ends up in the build, with
its license and, for Apache 2.0 and the BSD licenses, the required copyright notices. Adding a
dependency means adding it there as well, in the same commit.
