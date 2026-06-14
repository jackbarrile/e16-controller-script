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
    private static final int CC_MACRO = 12;       // knob 12 - unmapped, push toggles rec arm
    private static final int CC_VOLUME = 13;      // knob 13 — track volume
    private static final int CC_PAN = 14;         // knob 14 — track pan
    private static final int CC_TRACK_NAV = 15;   // knob 15 — prev/next track
    private static final int CC_DEVICE_NAV = 16;  // knob 16 — prev/next device
    private static final int CC_MACRO_PUSH = 32;  // Knob 12 push

    private static final int NUM_REMOTE_CONTROLS = 8;

    // -------------------------------------------------------------------------
    // Navigation — Accumulator Logic
    // -------------------------------------------------------------------------
    private static final int TICKS_PER_STEP = 3;

    private int trackNavAccumulator = 0;
    private int deviceNavAccumulator = 0;

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
            final int ccOut = CC_KNOB_FIRST + i;

            remoteControlsPage.getParameter(i).value().addValueObserver(128, val -> midiOut.sendMidi(0xB0 | MIDI_CHANNEL, ccOut, val));
        }

        cursorTrack.volume().value().addValueObserver(128, val -> midiOut.sendMidi(0xB0 | MIDI_CHANNEL, CC_VOLUME, val));

        cursorTrack.pan().value().addValueObserver(128, val -> midiOut.sendMidi(0xB0 | MIDI_CHANNEL, CC_PAN, val));

        cursorTrack.arm().addValueObserver(isArmed -> {
            int ledState = isArmed ? 127 : 0;
            midiOut.sendMidi(0xB0 | MIDI_CHANNEL, CC_MACRO, ledState);
        });

        // --- Knob 15: Track Nav (Percentage) ---
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

        // --- STANDARD CC BOUNCER ---
        if (type != 0xB0 || channel != MIDI_CHANNEL) return;

        if (cc >= CC_KNOB_FIRST && cc <= CC_KNOB_LAST) {
            final int slot = cc - CC_KNOB_FIRST;
            remoteControlsPage.getParameter(slot).set(value / 127.0);

        } else if (cc == CC_VOLUME) {
            cursorTrack.volume().set(value / 127.0);

        } else if (cc == CC_PAN) {
            cursorTrack.pan().set(value / 127.0);

        } else if (cc == CC_TRACK_NAV) {
            int rawDelta = relDelta(value);

            if (rawDelta != 0) {
                int direction = rawDelta > 0 ? 1 : -1;

                if ((direction > 0 && trackNavAccumulator < 0) ||
                        (direction < 0 && trackNavAccumulator > 0)) {
                    trackNavAccumulator = 0;
                }

                trackNavAccumulator += direction;

                if (trackNavAccumulator >= TICKS_PER_STEP) {
                    cursorTrack.selectPrevious();
                    trackNavAccumulator = 0;
                } else if (trackNavAccumulator <= -TICKS_PER_STEP) {
                    cursorTrack.selectNext();
                    trackNavAccumulator = 0;
                }
            }

        } else if (cc == CC_DEVICE_NAV) {
            int rawDelta = relDelta(value);

            if (rawDelta != 0) {
                int direction = rawDelta > 0 ? 1 : -1;

                if ((direction > 0 && deviceNavAccumulator < 0) ||
                        (direction < 0 && deviceNavAccumulator > 0)) {
                    deviceNavAccumulator = 0;
                }

                deviceNavAccumulator += direction;

                if (deviceNavAccumulator >= TICKS_PER_STEP) {
                    cursorDevice.selectPrevious();
                    deviceNavAccumulator = 0;
                } else if (deviceNavAccumulator <= -TICKS_PER_STEP) {
                    cursorDevice.selectNext();
                    deviceNavAccumulator = 0;
                }
            }

        } else if (cc == CC_MACRO_PUSH) {
            if (value > 0) {
                cursorTrack.arm().toggle();
                getHost().println("[KNOB 12] Single Tap! Rec Arm Toggled.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // LED Math Helpers
    // -------------------------------------------------------------------------
    private void updateTrackNavLed(final MidiOut midiOut) {
        int ledValue = 0;

        if (totalTracks > 1) {
            double percent = (double) currentTrackIdx / (totalTracks - 1);
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

    // -------------------------------------------------------------------------
    // Relative CC Decoder
    // -------------------------------------------------------------------------
    private static int relDelta(final int value) {
        if (value >= 1 && value <= 63) {
            return value; // CW
        } else if (value >= 65 && value <= 127) {
            return value - 128; // CCW
        }
        return 0;
    }
}