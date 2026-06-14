# OXI E16 - Bitwig Controller Script

A custom Java-based controller extension for Bitwig Studio that fully integrates the OXI E16. 

This script replaces the E16's internal page-based navigation with Bitwig's native context-aware Remote Controls system. It provides bidirectional communication, meaning the E16's LED rings instantly update to reflect the DAW's actual parameter values when you change tracks, select devices, or tweak settings with your mouse.

## Features
* **Context-Aware Top 8 Encoders:** Knobs 1–8 automatically map to the Remote Controls of your currently selected device. The hardware dynamically follows your focus in Bitwig.
* **True Bidirectional Sync:** Bitwig continuously pushes parameter updates back to the E16. Selecting a new synth or tweaking a parameter with your mouse instantly updates the E16's LED rings. No value jumping.
* **Anti-Windup Relative Navigation:** Custom accumulator logic translates the E16's `Rel 1` output into flawless track and device navigation, stripping out hardware acceleration and preventing deadzones when reversing direction.
* **Dynamic LED Progress Bars:** Knobs 15 and 16 utilize background observers to calculate your exact position within a project or device chain, using the LED rings as percentage-based progress bars.
* **One-Tap Record Arm:** Pushing Knob 12 instantly toggles Record Arm on the currently selected track.

## Hardware Configuration
Because of strict routing rules within the OXI firmware, the E16 requires a specific layout to communicate properly with the Bitwig API. 

To save you from manually mapping everything, a pre-configured `Bitwig.oxie16` scene file is included with this script. 

### Loading the Profile (Required)
1. Open the **OXI Desktop App** with your E16 connected via USB.
2. In the "Scenes" menu, load the provided `Bitwig.oxie16` file into the **On Computer** list.
3. Click the loaded Scene to open it in the Editor.
4. Close the editor. Drag and drop the `Bitwig` scene from the **On Computer** list directly onto **S.1** in the **On Device** list to permanently save it to the hardware.
5. Give the E16 a quick reboot to clear its cache. 

### What the Profile Does Under the Hood
If you ever need to recreate this profile manually, here is how the script expects the E16 to be configured:
* **Knobs 1–8 (Remote Controls):** Mode: `Absolute CC` (CCs 1–8).
* **Knobs 9–11 (Special):** Unmapped. Left as hardware default `Snapshot`, `Rec`, and `Random`.
* **Knobs 12–14 (Macro, Vol, Pan):** Mode: `Absolute CC` (CCs 12–14).
* **Knobs 15–16 (Nav):** Mode: **`Rel 1`** (CCs 15–16). *Absolute mode will cause navigation to hit a deadzone at 0 and 127.*
* **Knob 12 Push (Rec Arm):** Type: `CC`, Output: `USB1`, Channel: `1`, CC Number: `32`, Mode: `Press Only`, Value: `127`. *(Note: Ensure Knob 12's Special Turn Function is set to "Off", otherwise the hardware firmware will permanently swallow the push data).*

## Bitwig Setup & Installation

### Option A: Building from Source (Maven)
This project includes a `pom.xml` configured to automatically compile the script, rename the output to the required `.bwextension` format, and deploy it to your Bitwig folder.
1. Run `mvn clean install` in your terminal.
2. **Note for Windows Users:** The `pom.xml` is configured for macOS by default (`${user.home}/Documents/Bitwig Studio/Extensions`). If you are compiling on Windows, update the `<bitwig.extensions.dir>` property in the `pom.xml` to match your local path.

### Option B: Installing the Pre-Compiled Extension
If you are just using the pre-compiled extension, move the `OxiE16.bwextension` file into your Bitwig extensions directory:
* **Mac:** `~/Documents/Bitwig Studio/Extensions/`
* **Windows:** `%USERPROFILE%\Documents\Bitwig Studio\Extensions\`

### Controller Configuration in Bitwig
Once the `.bwextension` file is in your folder, restart Bitwig (or reload your scripts). 

* **Automatic Setup:** If you have *Settings > Controllers > "Add detected controller automatically"* checked, Bitwig will immediately discover the E16, load the script, and assign the MIDI I/O ports for you.
* **Manual Setup:** If auto-add is disabled:
  1. Open Bitwig Studio -> **Settings** -> **Controllers**.
  2. Click **Add Controller**.
  3. Search the Hardware Vendor list for **OXI Instruments** and select **E16**.
  4. Ensure both the **MIDI In** and **MIDI Out** menus are assigned to your physical OXI E16 USB ports.

## Troubleshooting
* **Script crashes on load / Knobs are unresponsive:** Ensure the Definition file declares exactly `1` MIDI Out port. If it declares `0`, Bitwig will abort the script upon attempting to route the LED feedback.
* **Record Arm push does nothing:** Confirm the output for the push action is explicitly set to `USB1` in the OXI App. If it is set to `ALL -USB`, the hardware will route the command to the physical TRS ports on the back of the unit and bypass your computer entirely. 
* **Changes in the OXI App aren't working:** Clicking "Save" in the OXI App only saves to your computer's hard drive. You must click **Set**, and then drag the scene to the "On Device" list to overwrite the hardware's internal memory.