# Layout diagnostics

An off-device check of the universal layout data model. It runs the real model classes on
a desktop JVM, so the parts of the feature that decide what a layout means — action
sequences, delays, combinations, configuration round trips and the upgrade of layouts
saved by an earlier version — can be verified without a device.

`org/json` is stubbed in `android.jar`, and the Android classes themselves throw, so the
harness ships a minimal `org/json` implementation and only exercises code that does not
touch the platform. That is deliberate: it is the boundary of what this check can cover.
Anything that needs a real `Context`, `View` or `InputDevice` still has to be tested on a
device.

## Running it

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$androidJar = "$sdk\platforms\android-37.0\android.jar"
$classes = "app\build\intermediates\javac\nonRootDebug\compileNonRootDebugJavaWithJavac\classes"
$out = "build-diagnostics\out"

gradlew.bat :app:compileNonRootDebugJavaWithJavac

javac -nowarn -cp "$androidJar;$classes" -d $out `
    build-diagnostics\shim\org\json\JSONObject.java `
    build-diagnostics\shim\org\json\JSONArray.java `
    build-diagnostics\shim\org\json\JSONException.java

javac -nowarn -cp "$out;$androidJar;$classes" -d $out `
    build-diagnostics\LayoutModelHarness.java

java -cp "$out;$classes;$androidJar" LayoutModelHarness
```

The last line prints `ALL CHECKS PASSED` and exits with code 0, or lists the failures and
exits with code 1.

The older platform version must be used if `android-37.0` is not installed; any platform
works, because the harness only needs the constant values from `KeyEvent`.

## What it covers

- action sequences survive a JSON round trip, including per step delays
- a combination (several inputs in one step) stays one step
- copying a configuration is deep, so duplicating an element cannot edit both
- button, stick and D-pad configurations round trip, including unassigned directions
- a layout saved before the element model existed still loads its geometry
- `KEYCODE_R` translates to the same GFE code the feature requires
- every gamepad button the editor offers resolves to a real controller flag
