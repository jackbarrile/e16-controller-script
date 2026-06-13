package com.jackbarrile.controllerscripts.e16;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

public class E16ExtensionDefinition extends ControllerExtensionDefinition {

    private static final UUID DRIVER_ID = UUID.fromString("7cad2a41-285e-4e2a-8277-f556b1545d76");

    public E16ExtensionDefinition() {}

    @Override
    public String getName() {
        return "OXI E16";
    }

    @Override
    public String getAuthor() {
        return "Jack Barrile";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public UUID getId() {
        return DRIVER_ID;
    }

    @Override
    public String getHardwareVendor() {
        return "OXI Instruments";
    }

    @Override
    public String getHardwareModel() {
        return "E16";
    }

    @Override
    public int getRequiredAPIVersion() {
        // Bitwig 6 API version. If the extension fails to load, check:
        // jar xf "/Applications/Bitwig Studio.app/Contents/lib/extensions/bitwig-extension-api.jar" META-INF/MANIFEST.MF
        // and update this value to match.
        return 19;
    }

    @Override
    public int getNumMidiInPorts() {
        return 1;
    }

    @Override
    public int getNumMidiOutPorts() {
        // No LED ring or OLED feedback supported by E16 firmware (as of v1).
        // Set to 1 to leave the door open for future PC scene-switching without
        // requiring a structural change to the extension.
        return 1;
    }

    @Override
    public void listAutoDetectionMidiPortNames(
            final AutoDetectionMidiPortNamesList list,
            final PlatformType platformType) {
        // Bitwig will auto-select this extension when the E16 is connected via USB.
        // The E16 presents itself as "OXI E16" on both macOS and Windows.
        list.add(new String[]{"OXI E16 Port 1"}, new String[]{"OXI E16 Port 1"});
    }

    @Override
    public E16Extension createInstance(final ControllerHost host) {
        return new E16Extension(this, host);
    }
}
