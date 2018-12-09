/* Copyright (c) 2018, Jesse DeGuire
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
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

package com.github.jdeguire.generatePic32SpecificStuff;

import com.microchip.crownking.Anomaly;
import java.util.List;
import java.util.ArrayList;

import com.microchip.crownking.mplabinfo.DeviceSupport;
import com.microchip.crownking.mplabinfo.DeviceSupport.Device;
import com.microchip.crownking.mplabinfo.DeviceSupportException;
import com.microchip.crownking.mplabinfo.FamilyDefinitions;
import com.microchip.crownking.mplabinfo.FamilyDefinitions.Family;
import com.microchip.crownking.mplabinfo.FamilyDefinitions.SubFamily;
import com.microchip.mplab.crownkingx.xPICFactory;
import com.microchip.mplab.crownkingx.xPIC;
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

   	private enum TargetArch {
		MIPS32R2,
		MIPS32R5,
		ARMV6M,
		ARMV7A,
		ARMV7M,
		ARMV7EM,
		ARMV8A,
		ARMV8M_BASE,
		ARMV8M_MAIN
	};

    private String outputDirBase_;

    /**
     * Constructor for the Stuff Generator.
     * 
     * @param output_dir    The base path for generated files.  The subclasses will add on to this
     *                      as needed to separate by header vs linker script and will add the device
     *                      name, so you should not do that.  Just pass the same path to every
     *                      instance to have everything properly organized.
     */
    public StuffGenerator(String output_dir) {
        outputDirBase_ = output_dir;
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
        xPIC target = (xPIC)xPICFactory.getInstance().get(device.getName());


        xPICFactory.getInstance().release(target);
    }

    /**
     * Get the name of the device provided to the constructor of this class, but in all uppercase.
     */
    private String getTargetName(xPIC target) {
        return target.getName().toUpperCase();
    }

    /**
     * Get the name of target's CPU, such as "Cortex-M7".
     */
    private String getCpuName(xPIC target) {
        return target.getArchitecture();
    }

    /**
     * Get the target's name formatted for use as a C macro as expected by the toolchain headers. 
     */
    private String getDeviceNameMacro(xPIC target) {
        String name = getTargetName(target);
        String res = "";

        if (name.startsWith("PIC32")) {
            res = "__" + name.substring(3) + "__";       // "__32MX795F512L__"
        } else if(name.startsWith("ATSAM")) {
            res = "__" + name.substring(2) + "__";       // "__SAME70Q21__"
        } else if (!name.isEmpty()) {
            res = "__" + name + "__";
        }

        return res;
    }

    /**
     * Get the device family of the target, which is used to determine its features.
     */
	private Family getFamily(xPIC target) {
		return target.getFamily();
	}

	/**
     * Get the subfamily of the target, which is a bit more fine-grained than the family.
	 */
	private SubFamily getSubFamily(xPIC target) {
		return target.getSubFamily();
	}
    
    /**
     * Get the instruction sets supported by the given target.  For example, MIPS devices may support
     * MIPS32 and microMIPS.
     */
    private ArrayList<String> getInstructionSets(xPIC target) {
   		ArrayList<String> instructionSets = new ArrayList<>(target.getInstructionSet().getSubsetIDs());

        String setId = target.getInstructionSet().getID();
        if(setId != null  &&  !setId.isEmpty())
            instructionSets.add(setId);

        return instructionSets;
    }
    
	/**
     * Get the CPU architecture for the given target.
	 */
	private TargetArch getArch(xPIC target) {
		TargetArch arch;

		if(isMips32(target)) {
			// The device database does not seem to distinguish between the two, but looking at the
			// datasheets indicates that devices with an FPU are MIPS32r5.
			if(hasFpu(target))
				arch = TargetArch.MIPS32R5;
			else
				arch = TargetArch.MIPS32R2;
		}
		else   // ARM32
		{
            ArrayList<String> instructionSets = getInstructionSets(target);
			arch = TargetArch.ARMV6M;   // lowest common denominator
            
			boolean found = false;
			for(int i = 0; !found  &&  i < instructionSets.size(); ++i) {
				switch(instructionSets.get(i).toLowerCase()) {
					case "armv6m":                               // Cortex M0, M0+, M1
						arch = TargetArch.ARMV6M;
						found = true;
						break;
					case "armv7a":                               // Cortex A5-A9, A1x
						arch = TargetArch.ARMV7A;
						found = true;
						break;
					case "armv7m":                               // Cortex M3
					case "armv7em":                              // Cortex M4, M7
						/* NOTE:  Microchip does not have any active M3 devices, so their database 
						          uses "armv7m" for M4 and M7 devices.	*/
						arch = TargetArch.ARMV7EM;
						found = true;
						break;
					case "armv8a":                               // Cortex A3x, A5x, A7x
						arch = TargetArch.ARMV8A;
						found = true;
						break;
					case "armv8m.base":                          // Cortex M23
						arch = TargetArch.ARMV8M_BASE;
						found = true;
						break;
					case "armv8m.main":                          // Cortex M33, M35P
						arch = TargetArch.ARMV8M_MAIN;
						found = true;
						break;
					default:
						found = false;
						break;
				}
			}
		}

		return arch;
	}

	/**
     * Get the name of the architecture as a string suitable for passing to Clang's "-march="
	 * option.  This will probably also work for GCC, though it has not been tried.
	 */
	private String getArchNameForClang(xPIC target) {
		return getArch(target).name().toLowerCase().replace('_', '.');
	}

    /**
     * Get the target triple name used by the toolchain to determine the overall architecture
     * in use.  This is used with the "-target" compiler option.
     */
    private String getTargetTripleName(xPIC target) {
		if(isMips32(target))
			return "mipsel-unknown-elf";
		else
			return "arm-none-eabi";
    }

    /**
     * Return True if the target is a MIPS32 device.
     */
    private boolean isMips32(xPIC target) {
        return target.getFamily() == Family.PIC32;
    }

    /**
     * Return True if the target is an ARM device.
     */
    private boolean isArm(xPIC target) {
        return target.getFamily() == Family.ARM32BIT;
    }

    /**
     * Return True if the target has an FPU.
     */
    public boolean hasFpu(xPIC target) {
        return target.hasFPU();
    }

    /**
     * Return True if the target supports the MIPS32 instruction set.
     */
    private boolean supportsMips32Isa(xPIC target) {
        return (isMips32(target)  &&  target.getSubFamily() != FamilyDefinitions.SubFamily.PIC32MM);
    }

    /* Return True if the target supports the MIPS16e instruction set.
     */
    private boolean supportsMips16Isa(xPIC target) {
        return (isMips32(target)  &&  target.has16Mips());
    }

    /* Return True if the target supports the microMIPS instruction set.
     */
    private boolean supportsMicroMipsIsa(xPIC target) {
        return (isMips32(target)  &&  target.hasMicroMips());
    }

    /* Return True if the target supports the MIPS DSPr2 application specific extension.
     */
    private boolean supportsDspr2Ase(xPIC target) {
		boolean hasDsp = false;

		if(isMips32(target)) {
            ArrayList<String> instructionSets = getInstructionSets(target);
            for(String id : instructionSets) {
				if(id.equalsIgnoreCase("dspr2")) {
					hasDsp = true;
					break;
				}
			}
		}

		return hasDsp;
    }

    /* Return True if the target supports the ARM (as opposed to the compressed Thumb) instruction set.
     */
    private boolean supportsArmIsa(xPIC target) {
		TargetArch arch = getArch(target);

        return (TargetArch.ARMV7A == arch  ||  TargetArch.ARMV8A == arch);
    }

    /* Return True if the target supports the Thumb instruction set.
     */
    private boolean supportsThumbIsa(xPIC target) {
        return isArm(target);
    }

    /* Get the name of the FPU for ARM devices.  ARM devices have different FPU variants that can be
     * supported that determine whether it is single-precision only or also supports double-precision
     * as well as how many FPU registers are available and whether NEON SIMD extensions are supported.
     *
     * MIPS devices have only only FPU, so this will return an empty string for MIPS.
     */
    private String getArmFpuName(xPIC target) {
		String fpuName = "";

		if(isArm(target)  &&  hasFpu(target)) {
			TargetArch arch = getArch(target);

			if(TargetArch.ARMV7M == arch  ||  TargetArch.ARMV7EM == arch) {
				if(getCpuName(target).equalsIgnoreCase("Cortex-M7"))
					fpuName = "vfp5-dp-d16";
				else
					fpuName = "vfp4-sp-d16";
			}
			else if(TargetArch.ARMV7A == arch) {
                // There does not yet seem to be a way to check for NEON other than name.
                String name = getTargetName(target);
                
                if(name.startsWith("SAMA5D3")  ||  name.startsWith("ATSAMA5D3"))
                    fpuName = "neon-vfpv4";
                else
                    fpuName = "vfp4-dp-d16";
            }
			else {
				fpuName = "vfp4-dp-d16";
			}
		}

        return fpuName;
    }
}
