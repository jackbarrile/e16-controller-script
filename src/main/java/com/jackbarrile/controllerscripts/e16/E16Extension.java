package com.jackbarrile.controllerscripts.e16;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.*;

public class E16Extension extends ControllerExtension {

    // -------------------------------------------------------------------------
    // CC assignments (MIDI channel 1, 0-indexed = 0)
    // -------------------------------------------------------------------------
    private static final int MIDI_CHANNEL = 0;   // channel 1

    private static final int CC_KNOB_FIRST = 1;   // knob 1
    private static final int CC_KNOB_LAST = 8;   // knob 8
    // N.B. CC_MACRO = 12 in unmapped by design
    private static final int CC_VOLUME = 13;  // knob 13 — track volume
    private static final int CC_PAN = 14;  // knob 14 — track pan
    private static final int CC_TRACK_NAV = 15;  // knob 15 — prev/next track
    private static final int CC_DEVICE_NAV = 16;  // knob 16 — prev/next device

    private static final int NUM_REMOTE_CONTROLS = 8;

    // -------------------------------------------------------------------------
    // Navigation — Accumulator Logic
    //
    // Tune TICKS_PER_STEP:
    // If your encoder sends 1 CC message per physical detent, leave this at 1.
    // If the encoder sends 4 messages before you feel a physical "click", set this to 4.
    // -------------------------------------------------------------------------
    private static final int TICKS_PER_STEP = 3;

    private int trackNavAccumulator = 0;
    private int deviceNavAccumulator = 0;

    private int lastTrackNavRaw = -1;
    private int lastDeviceNavRaw = -1;

    // State trackers for LED math
    private int currentTrackIdx = 0;
    private int totalTracks = 1;

    private int currentDeviceIdx = 0;
    private int totalDevices = 1;

    // -------------------------------------------------------------------------
    // Bitwig API objects
    // -------------------------------------------------------------------------
    private CursorTrack cursorTrack;
    private PinnableCursorDevice cursorDevice;
    private CursorRemoteControlsPage remoteControlsPage;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    protected E16Extension(final E16ExtensionDefinition definition, final ControllerHost host) {
        super(definition, host);
    }

    @Override
    public void init() {
        final ControllerHost host = getHost();

        cursorTrack = host.createCursorTrack(0, 0);
        cursorDevice = cursorTrack.createCursorDevice();
        remoteControlsPage = cursorDevice.createCursorRemoteControlsPage(NUM_REMOTE_CONTROLS);

        final MidiIn midiIn = host.getMidiInPort(0);
        midiIn.createNoteInput("OXI E16", "80????", "90????");
        midiIn.setMidiCallback(this::onMidi);

        // --- Hardware Visual Feedback (LED Rings) ---
        final MidiOut midiOut = host.getMidiOutPort(0);

        for (int i = 0; i < NUM_REMOTE_CONTROLS; i++) {
            final int ccOut = CC_KNOB_FIRST + i; // Maps to CCs 1-8

            // The "128" tells Bitwig to scale the parameter to a 0-127 integer range
            remoteControlsPage.getParameter(i).value().addValueObserver(128, val -> {
                // Send the absolute value back to the E16 to update the LED ring
                midiOut.sendMidi(0xB0 | MIDI_CHANNEL, ccOut, val);
            });
        }

        cursorTrack.volume().value().addValueObserver(128, val -> midiOut.sendMidi(0xB0 | MIDI_CHANNEL, CC_VOLUME, val));

        cursorTrack.pan().value().addValueObserver(128, val -> midiOut.sendMidi(0xB0 | MIDI_CHANNEL, CC_PAN, val));

        // --- Knob 15: Track Nav (Percentage) ---
        // Create a 128-slot bank so itemCount() can see up to 128 tracks
        TrackBank trackBank = host.createTrackBank(128, 0, 0);

        trackBank.itemCount().addValueObserver(count -> {
            totalTracks = count;
            updateTrackNavLed(midiOut);
        });

        cursorTrack.position().addValueObserver(pos -> {
            currentTrackIdx = pos;
            updateTrackNavLed(midiOut);
        });

        // --- Knob 16: Device Nav (Percentage) ---
        // Create a 128-slot bank so itemCount() can see up to 128 devices
        DeviceBank deviceBank = cursorTrack.createDeviceBank(128);

        deviceBank.itemCount().addValueObserver(count -> {
            totalDevices = count;
            updateDeviceNavLed(midiOut);
        });

        cursorDevice.position().addValueObserver(pos -> {
            currentDeviceIdx = pos;
            updateDeviceNavLed(midiOut);
        });

        host.showPopupNotification("OXI E16 connected");
    }

    @Override
    public void exit() {
        getHost().showPopupNotification("OXI E16 disconnected");
    }

    @Override
    public void flush() {
        // No MIDI output in this version.
    }

    // -------------------------------------------------------------------------
    // MIDI handling
    // -------------------------------------------------------------------------
    private void onMidi(final int status, final int cc, final int value) {
        final int type = status & 0xF0;
        final int channel = status & 0x0F;

        if (type != 0xB0 || channel != MIDI_CHANNEL) return;

        if (cc >= CC_KNOB_FIRST && cc <= CC_KNOB_LAST) {
            final int slot = cc - CC_KNOB_FIRST;
            remoteControlsPage.getParameter(slot).set(value / 127.0);

        } else if (cc == CC_VOLUME) {
            cursorTrack.volume().set(value / 127.0);

        } else if (cc == CC_PAN) {
            cursorTrack.pan().set(value / 127.0);

        } else if (cc == CC_TRACK_NAV) {
            // 1. Initialize baseline on the very first touch
            if (lastTrackNavRaw == -1) {
                lastTrackNavRaw = value;
                return;
            }

            // 2. Calculate true mathematical delta
            int rawDelta = value - lastTrackNavRaw;

            // 3. Handle endless encoder wrap-around (e.g., 127 stepping up to 0)
            if (rawDelta > 64) rawDelta -= 128;
            else if (rawDelta < -64) rawDelta += 128;

            lastTrackNavRaw = value;

            if (rawDelta != 0) {
                // 4. Strip acceleration: force delta to exactly +1 or -1
                int direction = rawDelta > 0 ? 1 : -1;

                // 5. Anti-Windup
                if ((direction > 0 && trackNavAccumulator < 0) ||
                        (direction < 0 && trackNavAccumulator > 0)) {
                    trackNavAccumulator = 0;
                }

                trackNavAccumulator += direction;

                // 6. Hard reset on fire
                if (trackNavAccumulator >= TICKS_PER_STEP) {
                    cursorTrack.selectNext();
                    trackNavAccumulator = 0;
                } else if (trackNavAccumulator <= -TICKS_PER_STEP) {
                    cursorTrack.selectPrevious();
                    trackNavAccumulator = 0;
                }
            }

        } else if (cc == CC_DEVICE_NAV) {
            if (lastDeviceNavRaw == -1) {
                lastDeviceNavRaw = value;
                return;
            }

            int rawDelta = value - lastDeviceNavRaw;

            if (rawDelta > 64) rawDelta -= 128;
            else if (rawDelta < -64) rawDelta += 128;

            lastDeviceNavRaw = value;

            if (rawDelta != 0) {
                int direction = rawDelta > 0 ? 1 : -1;

                if ((direction > 0 && deviceNavAccumulator < 0) ||
                        (direction < 0 && deviceNavAccumulator > 0)) {
                    deviceNavAccumulator = 0;
                }

                deviceNavAccumulator += direction;

                if (deviceNavAccumulator >= TICKS_PER_STEP) {
                    cursorDevice.selectNext();
                    deviceNavAccumulator = 0;
                } else if (deviceNavAccumulator <= -TICKS_PER_STEP) {
                    cursorDevice.selectPrevious();
                    deviceNavAccumulator = 0;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // LED Math Helpers
    // -------------------------------------------------------------------------

    private void updateTrackNavLed(final MidiOut midiOut) {
        int ledValue = 0;

        if (totalTracks > 1) {
            // Calculate percentage: current / max_index
            double percent = (double) currentTrackIdx / (totalTracks - 1);

            // Clamp to 0-1 just in case, then scale to MIDI 0-127
            percent = Math.max(0.0, Math.min(1.0, percent));
            ledValue = (int) Math.round(percent * 127);
        }

        midiOut.sendMidi(0xB0 | MIDI_CHANNEL, CC_TRACK_NAV, ledValue);
    }

    private void updateDeviceNavLed(final MidiOut midiOut) {
        int ledValue = 0;

        if (totalDevices > 1) {
            double percent = (double) currentDeviceIdx / (totalDevices - 1);
            percent = Math.max(0.0, Math.min(1.0, percent));
            ledValue = (int) Math.round(percent * 127);
        }

        midiOut.sendMidi(0xB0 | MIDI_CHANNEL, CC_DEVICE_NAV, ledValue);
    }

}