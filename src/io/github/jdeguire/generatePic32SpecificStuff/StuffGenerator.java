/* Copyright (c) 2020, Jesse DeGuire
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.github.jdeguire.generatePic32SpecificStuff;

import com.microchip.crownking.Anomaly;
import java.util.List;
import java.util.ArrayList;

import com.microchip.crownking.mplabinfo.DeviceSupport;
import com.microchip.crownking.mplabinfo.DeviceSupport.Device;
import com.microchip.crownking.mplabinfo.DeviceSupportException;
import com.microchip.crownking.mplabinfo.FamilyDefinitions.Family;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;


/**
 * This class is what actually generates device-specific header files, linker scripts, and Clang
 * configuration files (these contain command-line options for the device).  To use, create a new 
 * instance while specifying the desired output directory for the generated files.  Call the
 * <code>getDeviceList()</code> method to get a list of MIPS (PIC32) and ARM devices.  Then, call
 * the <code>generate()</code> method for each device for while files should be generated.
 */
public class StuffGenerator {

    private final String outputDirBase_;
    CortexMLinkerScriptGenerator cortexmLinkerGen_;
    CortexMLegacyHeaderFileGenerator cortexmLegacyHeaderGen_;
    CortexMHeaderFileGenerator cortexmHeaderGen_;
    CortexMStartupGenerator cortexmStartupGen_;
    MipsLinkerScriptGenerator mipsLinkerGen_;
    MipsHeaderFileGenerator mipsHeaderGen_;
    TargetConfigGenerator targetConfigGen_;
    XcHeaderGenerator cortexmXcGen_;
    XcHeaderGenerator mipsXcGen_;

    /**
     * Constructor for the Stuff Generator.
     * 
     * @param outputDir    The base path for generated files.  The subclasses will add on to this
     *                     as needed to separate by header vs linker script and will add the device
     *                     name.
     */
    public StuffGenerator(String outputDir) {
        outputDirBase_ = outputDir + "/target/";

        String cortexmLinkerDir = outputDirBase_ + "cortex-m/lib/proc";
        String cortexmLegacyHeaderDir = outputDirBase_ + "cortex-m/include/proc_legacy";
        String cortexmHeaderDir = outputDirBase_ + "cortex-m/include/proc";
        String cortexmStartupDir = outputDirBase_ + "cortex-m/lib/proc";
        String mipsLinkerDir = outputDirBase_ + "mips32/lib/proc";
        String mipsHeaderDir = outputDirBase_ + "mips32/include/proc";
        String targetConfigDir = outputDirBase_ + "config";
        String cortexmXcDir = outputDirBase_ + "cortex-m/include";
        String mipsXcDir = outputDirBase_ + "mips32/include";

        cortexmLinkerGen_ = new CortexMLinkerScriptGenerator(cortexmLinkerDir);
        cortexmLegacyHeaderGen_ = new CortexMLegacyHeaderFileGenerator(cortexmLegacyHeaderDir);
        cortexmHeaderGen_ = new CortexMHeaderFileGenerator(cortexmHeaderDir);
        cortexmStartupGen_ = new CortexMStartupGenerator(cortexmStartupDir);

        mipsLinkerGen_ = new MipsLinkerScriptGenerator(mipsLinkerDir);
        mipsHeaderGen_ = new MipsHeaderFileGenerator(mipsHeaderDir);

        targetConfigGen_ = new TargetConfigGenerator(targetConfigDir);

        cortexmXcGen_ = new XcHeaderGenerator(cortexmXcDir, cortexmHeaderDir, cortexmLegacyHeaderDir);
        mipsXcGen_ = new XcHeaderGenerator(mipsXcDir, mipsHeaderDir, "");
    }

    
    /**
     * Get the list of MIPS32 (PIC32) and ARM devices found in the MPLAB X device database.  Pass
     * each device to the <code>generate()</code> method to actually generate the files.
     * 
     * @return The list of devices.
     * @throws DeviceSupportException
     */
    public List<Device> getDeviceList() throws DeviceSupportException {
        DeviceSupport deviceSupport = DeviceSupport.getInstance();
        List<Device> deviceList = deviceSupport.getDevices();
        ArrayList<Device> resultList = new ArrayList<>(256);

        for(Device device : deviceList) {
            Family family = device.getFamily();
            if(Family.ARM32BIT == family  ||  Family.PIC32 == family)
                resultList.add(device);
        }

        return resultList;
    }

    /** 
     * Call this once before calling <code>generate()</code> for each device to allow this class to
     * do any needed operations before actually generating code.
     */
    public void startGenerate() {
        cortexmXcGen_.reset();
        mipsXcGen_.reset();
    }

    /**
     * Generate the files needed for this particular device.  This will generate a Clang config file
     * for the device, its device-specific header files, and a default linker script.  This will also
     * add an entry to an "xc.h" file for the device, just like Microchip's XC toolchains have.
     * 
     * This may throw a variety of exceptions for things such as XML parsing errors (the device 
     * database files are XML), file IO errors, or issues parsing device data from an otherwise 
     * valid XML file.
     * 
     * @param device    The device for which to generate the files.
     *
     * @throws Anomaly
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException 
     */
    public void generate(Device device)
            throws Anomaly, SAXException, IOException, ParserConfigurationException {
        TargetDevice target = new TargetDevice(device);

        // Generate the full output for just a few devices for now so we can more easily 
        // troubleshoot and verify output.
        if("PIC32MX795F512L".equalsIgnoreCase(device.getName())  ||
           "ATSAME54P20A".equalsIgnoreCase(device.getName()) ||
           "ATSAME70Q21B".equalsIgnoreCase(device.getName()) ||
           "PIC32MZ2048EFH144".equalsIgnoreCase(device.getName())) {

            if(target.isArm()) {
                // TODO:  We'll need to target Cortex-A devices in the future.
                if(!target.supportsArmIsa()) {
                    cortexmLinkerGen_.generate(target);
                    cortexmLegacyHeaderGen_.generate(target);
                    cortexmHeaderGen_.generate(target); 
                    cortexmStartupGen_.generate(target);
                    cortexmXcGen_.add(target);
                }
            } else {
                mipsLinkerGen_.generate(target);
                mipsHeaderGen_.generate(target);
                mipsXcGen_.add(target);
            }

            targetConfigGen_.generate(target);
        } else {
            // We'll add everything to the XC files because that will let us verify that those are
            // being generated correctly.
            if(target.isArm()) {
                if(!target.supportsArmIsa()) {
                    cortexmXcGen_.add(target);
                }
            } else {
                mipsXcGen_.add(target);
            }
        }
    }

    /** 
     * Call this once after calling <code>generate()</code> for each device to allow this class to
     * do any needed cleanup or finishing tasks after the code has been generated.
     */
    public void finishGenerate() throws FileNotFoundException {
        cortexmXcGen_.generate();
        mipsXcGen_.generate();
    }
}