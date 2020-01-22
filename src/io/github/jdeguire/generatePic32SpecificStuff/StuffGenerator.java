/* Copyright (c) 2019, Jesse DeGuire
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
import com.microchip.crownking.Pair;
import com.microchip.crownking.edc.DCR;
import java.util.List;
import java.util.ArrayList;

import com.microchip.crownking.mplabinfo.DeviceSupport;
import com.microchip.crownking.mplabinfo.DeviceSupport.Device;
import com.microchip.crownking.mplabinfo.DeviceSupportException;
import com.microchip.crownking.mplabinfo.FamilyDefinitions.Family;
import com.microchip.mplab.crownkingx.xMemoryPartition;
import com.microchip.mplab.crownkingx.xPIC;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
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
    CortexMLinkerScriptBuilder cortexmLinkerBuilder_;
    CortexMHeaderFileBuilder cortexmHeaderBuilder_;
    MipsLinkerScriptBuilder mipsLinkerBuilder_;
    
    /**
     * Constructor for the Stuff Generator.
     * 
     * @param outputDir    The base path for generated files.  The subclasses will add on to this
     *                     as needed to separate by header vs linker script and will add the device
     *                     name.
     */
    public StuffGenerator(String outputDir) {
        outputDirBase_ = outputDir;

        cortexmLinkerBuilder_ = new CortexMLinkerScriptBuilder(outputDirBase_ + "/cortex-m/lib/proc");
        cortexmHeaderBuilder_ = new CortexMHeaderFileBuilder(outputDirBase_ + "/cortex-m/include/proc");

        mipsLinkerBuilder_ = new MipsLinkerScriptBuilder(outputDirBase_ + "/mips32/lib/proc");
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
        ArrayList<Device> resultList = new ArrayList<>(100);

        for(Device device : deviceList) {
            Family family = device.getFamily();
            if(Family.ARM32BIT == family  ||  Family.PIC32 == family)
                resultList.add(device);
        }

        return resultList;
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
        TargetDevice target = new TargetDevice(device.getName());

        if(target.isArm()) {
            // TODO:  We'll need to target Cortex-A devices in the future.
            if(!target.supportsArmIsa()) {
                cortexmLinkerBuilder_.generate(target);
                cortexmHeaderBuilder_.generate(target);
            }
        } else {
            mipsLinkerBuilder_.generate(target);
        }
    }
}