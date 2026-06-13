# OXI E16 Bitwig Controller Script – Documentation

### **Goal**

Replace the E16's page-based navigation with Bitwig's native Remote Controls system, enabling full bidirectional communication. This provides context-aware top 8 encoders that automatically update when the selected device changes, and ensures the E16's LED rings always reflect actual Bitwig values.

*Note on Hardware Limitations: As of current firmware, the E16 does not support SysEx text updates via MIDI. Parameter names on the OLED remain static based on the OXI Desktop App configuration.*

### **Why It's Needed**

Without a script, the E16 operates one-way (sends CC, Bitwig listens) with no visual feedback. The OLED and LED rings show the E16's internal state, not Bitwig's actual parameter values. This causes sudden value jumps when the two get out of sync. Furthermore, standard generic MIDI mapping cannot dynamically shift focus to the "currently selected" track or device.

### **The Approach (V1 Implementation)**

Built as a custom Bitwig Controller Extension using the Bitwig Java API. The extension handles:

- **Bidirectional CC Mapping (LED Sync):** Bitwig Value Observers send current parameter states back to the E16 to instantly update the LED rings, keeping hardware and software in perfect sync.
- **Context-Aware Top 8:** Automatically maps knobs 1–8 to the Remote Controls page of the currently selected device. Updates instantly on device selection change.
- **Absolute Nav Accumulator:** Bypasses E16 relative mode hardware bugs by mathematically tracking Absolute CC deltas, filtering out acceleration, and preventing reversal "windup" deadzones.
- **LED Progress Bars:** Spawns background 128-slot Track and Device banks to calculate project size, mapping Knobs 15 & 16's LED rings to act as dynamic progress bars (0% = first track, 100% = last track).
- **Double-Tap Parameter Reset:** Tracks hardware encoder push states (via dedicated push CCs) with millisecond precision, resetting Bitwig parameters to default if a double-tap is detected within 300ms.

### **Key Insight**

With the script in place, E16 pages become mostly redundant for DAW use. Bitwig's Remote Controls system replaces the page layer entirely. The bottom 8 encoders remain static (Snapshot, Rec, Random, macros, navigation) while the top 8 are fully dynamic.

### **The CC Absolute vs. Relative Solution**

- **The Problem:** Relative modes (CC Rel 1/2) on the E16 hardware paired with Bitwig's relative CC Value Mode options produce jumpy, reversed, or ignored behavior. The E16 often defaults to Absolute output regardless of App configuration.
- **The Script Solution:** All E16 Knobs are set to **CC Abs** in the OXI App.
    - *For Parameters (Knobs 1-8, 12-14):* Direct absolute mapping. Value jumps are mitigated entirely because the script pushes Bitwig's values *back* to the E16's LED rings. The moment you select a device, the hardware adopts the software's state.
    - *For Navigation (Knobs 15-16):* The script tracks the Absolute CC values, calculates the exact mathematical delta between frames, handles the 127-to-0 encoder wrap-around, and outputs a strict 1-to-1 movement without acceleration artifacts.

### **Knob Layout (Final)**

- **Knobs 1–8:** Context-aware device parameters. Mirrors active Remote Controls page for the selected device. Updates on device change. LED rings synced.
- **Knob 9:** Snapshot morph fader (Type: Snapshot). Handled by hardware.
- **Knob 10:** Rec encoder (Special: Rec) — per-encoder automation looping. Handled by hardware.
- **Knob 11:** Random encoder (Special: Random) — randomize included parameters with optional slew. Handled by hardware.
- **Knob 12:** Global Macro. Mapped to Bitwig's Project-Level Remote Controls (e.g., global filter or master effects). LED ring synced.
- **Knob 13:** Cursor Track Volume. LED ring synced.
- **Knob 14:** Cursor Track Panning. LED ring synced.
- **Knob 15:** Prev/Next Track. Powered by Anti-Windup Absolute Accumulator. LED ring displays track position percentage (e.g., Track 1 = empty ring, Track 10 = full ring).
- **Knob 16:** Prev/Next Device. Powered by Anti-Windup Absolute Accumulator. LED ring displays device position percentage.

### **Parameter Reset**

To enable reset functionality without sacrificing the E16's hardware Rec/Automation features, we mapped the physical "Push" action of Knobs 1-8 to a hidden block of MIDI CCs (CC 21-28) in the OXI App.
The controller script silently monitors these CCs. If a user **double-taps** an encoder within 300ms, the script fires Bitwig's `reset()` API method, snapping the targeted parameter back to its default state.

### **Status**

- No official OXI E16 Bitwig script existed as of early 2026.
- **Custom V1 Script Implemented.** Basic bidirectional sync, navigation accumulators, LED feedback, and double-tap resets are actively functioning.