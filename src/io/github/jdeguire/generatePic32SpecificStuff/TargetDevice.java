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

import com.microchip.crownking.edc.DCR;
import com.microchip.crownking.edc.SFR;
import com.microchip.crownking.mplabinfo.DeviceSupport.Device;
import com.microchip.crownking.mplabinfo.FamilyDefinitions.Family;
import com.microchip.crownking.mplabinfo.FamilyDefinitions.SubFamily;
import com.microchip.mplab.crownkingx.xMemoryPartition;
import com.microchip.mplab.crownkingx.xPICFactory;
import com.microchip.mplab.crownkingx.xPIC;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * This class represents a target device supported by the toolchain and allows one to query the
 * device for features such as a floating-point unit or DSP extensions.  This uses the device
 * database built into MPLAB X (accessed via the xPIC object) to get information.  Much of the
 * xPIC-related stuff was adapted from example code provided by George Pauley, the MPLAB X 
 * Simulator team lead at Microchip.
 */
public class TargetDevice {

	public enum TargetArch {
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

    final private xPIC pic_;
    final private String name_;
	private ArrayList<String> instructionSets_;
    private ArrayList<LinkerMemoryRegion> lmrList_ = null;
    private ArrayList<SFR> sfrList_ = null;
    private ArrayList<DCR> dcrList_ = null;
    private HashMap<String, Long> sfrOffsetMap_ = null;
    private HashMap<String, Long> dcrOffsetMap_ = null;
    private InterruptList interruptList_ = null;
    private AtdfDoc atdfDoc_ = null;

    /* Create a new TargetDevice based on the given Device object.  Throws an exception if the given 
     * device is not recognized by this class or if the device does not represent a MIPS or Arm
     * device.
     */
    public TargetDevice(Device device) throws com.microchip.crownking.Anomaly, 
		org.xml.sax.SAXException,
		java.io.IOException, 
		javax.xml.parsers.ParserConfigurationException, 
		IllegalArgumentException {

        String devname = device.getName();

        pic_ = (xPIC)xPICFactory.getInstance().get(devname);
        name_ = normalizeDeviceName(devname);

        if(Family.PIC32 == pic_.getFamily()  ||  Family.ARM32BIT == pic_.getFamily()) {
   			instructionSets_ = new ArrayList<>(pic_.getInstructionSet().getSubsetIDs());

			String setId = pic_.getInstructionSet().getID();
			if(setId != null  &&  !setId.isEmpty())
				instructionSets_.add(setId);
        }
		else {
            String what = "Device " + devname + " is not a recognized MIPS32 or Arm device.";
            throw new IllegalArgumentException(what);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            xPICFactory.getInstance().release(pic_);
        } finally {
            super.finalize();
        }
    }

    /* Get the xPIC object used by this class to access the MPLAB X device database.
     */
    public xPIC getPic() {
        return pic_;
    }

    /* Get the name of the device this class represents.  It will be in uppercase and normalized
     * such that PIC32 devices will start with "PIC32" and SAM devices will start with "ATSAM".
     */
    public String getDeviceName() {
        return name_;
    }

    /* Return the name of the device modified such that it can be used for C macros.  How the name 
     * is modified depends on the device, but an example is that "PIC32" devices will have the 
     * "PIC" portion removed and "ATSAM" devices will have the "AT" removed.
     */
    public String getDeviceNameForMacro() {
        String name = getDeviceName();

        if (name.startsWith("PIC32")) {
            return name.substring(3);                 // "32MX795F512L"
        } else if(name.startsWith("ATSAM")) {
            return name.substring(2);                 // "SAME70Q21"
        } else {
            return name;
        }
    }

    /* Return a string that can be used as a device series, such as "PIC32MX", "SAMD", and so on.
     * For MIPS devices, this uses the device name to determine the series and will handle non-PIC32
     * devices as well.  For Arm devices, this is equivalent to calling getAtdfFamily().
     */
    public String getDeviceSeriesName() {
        if(isMips32()) {
            String devname = getDeviceName();

            if(devname.startsWith("M")) {
                return devname.substring(0, 3);
            } else if(devname.startsWith("USB")) {
                return devname.substring(0, 5);
            } else {
                return devname.substring(0, 7);
            }
        } else {
            return getAtdfFamily();
        }
    }

    /* Get the device family of the target, which is used to determine its features.
     */
	public Family getFamily() {
		return pic_.getFamily();
	}

	/* Get the subfamily of the target, which is a bit more fine-grained than the family.
	 */
	public SubFamily getSubFamily() {
		return pic_.getSubFamily();
	}

    /* Return the name of the ATDF device family for this device, such as "SAME" or "PIC32CX", that 
     * applies to Arm devices.  This will return an empty string if a family is not provided.  MIPS
     * device will generally not have an ATDF family, at least not at the time of this writing 
     * (2 Feb 2020).
     */
    public String getAtdfFamily() {
        String atdfFamily = pic_.getATDFFamily();

        if(null == atdfFamily) {
            atdfFamily = "";
        }

        return atdfFamily;
    }

    /* Get the CPU architecture for the device.
	 */
	public TargetArch getArch() {
		TargetArch arch;

		if(isMips32()) {
			// The device database does not seem to distinguish between the two, but looking at the
			// datasheets indicates that devices with an FPU are MIPS32r5.
			if(hasFpu())
				arch = TargetArch.MIPS32R5;
			else
				arch = TargetArch.MIPS32R2;
		}
		else   // ARM32
		{
			arch = TargetArch.ARMV6M;   // lowest common denominator

			boolean found = false;
			for(int i = 0; !found  &&  i < instructionSets_.size(); ++i) {
				switch(instructionSets_.get(i).toLowerCase()) {
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
                        // NOTE:  Microchip's EDC files do not actually distinguish between ARMv7-M
                        //        and ARMV7E-M, so we'll do it here.
                        if(getCpuName().equals("cortex-m3"))
    						arch = TargetArch.ARMV7M;
                        else
                            arch = TargetArch.ARMV7EM;

                        found = true;
						break;
					case "armv8a":                               // Cortex A3x, A5x, A7x
						arch = TargetArch.ARMV8A;
						found = true;
						break;
                    case "armv8m":
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

	/* Get the name of the architecture as a string suitable for passing to Clang's "-march="
	 * option.  This will probably also work for GCC, though it has not been tried.
	 */
	public String getArchNameForCompiler() {
		return getArch().name().toLowerCase().replace('_', '.');
	}

    /* Get the target triple name used by the toolchain to determine the overall architecture
     * in use.  This is used with the "-target" compiler option.
     */
    public String getTargetTripleName() {
		if(isMips32())
			return "mipsel-unknown-elf";
		else
			return "arm-none-eabi";
    }

    /* Get the CPU name to be used with Clang's "-mtune=" option, such as "cortex-m7" or "mips32r2".
     */
    public String getCpuName() {
        if(isMips32())
            return getArchNameForCompiler();
        else
            return pic_.getArchitecture().toLowerCase();
    }

    /* Return True if this is a MIPS32 device.
     */
    public boolean isMips32() {
        return getFamily() == Family.PIC32;
    }

    /* Return True if this is an ARM device.
     */
    public boolean isArm() {
        return getFamily() == Family.ARM32BIT;
    }

    /* Return True if the target has an FPU.
     */
    public boolean hasFpu() {
        boolean hasfpu = false;

        if(isMips32()) {
            hasfpu = pic_.hasFPU();
        } else {
            // The .PIC files don't encode this the same way for Arm devices, so we have to dig for
            // it ourselves.

            // We don't need the "edc:" prefix here since we're using the MPLAB X API.
            Node peripheralListNode = pic_.first("PeripheralList");

            if(null != peripheralListNode) {
                // We do need the "edc:" prefix here because this is not part of the MPLAB X API.
                Node fpuNode = Utils.filterFirstChildNode(peripheralListNode,
                                                          "edc:Peripheral",
                                                          "edc:cname",
                                                          "FPU");

                if(null != fpuNode) {
                    hasfpu = true;
                }
            }
        }

        return hasfpu;
    }

    /* Return True if the target has a 64-bit FPU.
     */
    public boolean hasFpu64() {
        // So far, only the Cortex-M4 devices have a single-precision FPU.
        return hasFpu()  &&  !getCpuName().equals("cortex-m4");
    }

    /* Return True if the device has an L1 cache.  This is actually just a guess for now based on
     * the device's family or architecture.
     */
    public boolean hasL1Cache() {
        boolean result = false;
        
        if(getSubFamily() == SubFamily.PIC32MZ) {
            result = true;
        } else if(getCpuName().equals("cortex-m7")) {
            result = true;
        } else if(getCpuName().startsWith("cortex-a")) {
            result = true;
        }

        return result;
    }

    /* Return True if the target supports the MIPS32 instruction set.
     */
    public boolean supportsMips32Isa() {
        return (isMips32()  &&  getSubFamily() != SubFamily.PIC32MM);
    }

    /* Return True if the target supports the MIPS16e instruction set.
     */
    public boolean supportsMips16Isa() {
        return (isMips32()  &&  pic_.has16Mips());
    }

    /* Return True if the target supports the microMIPS instruction set.
     */
    public boolean supportsMicroMipsIsa() {
        return (isMips32()  &&  pic_.hasMicroMips());
    }

    /* Return True if the target supports the MIPS DSPr2 application specific extension.
     */
    public boolean supportsDspR2Ase() {
		boolean hasDsp = false;

		if(isMips32()) {
			for(String id : instructionSets_) {
				if(id.equalsIgnoreCase("dspr2")) {
					hasDsp = true;
					break;
				}
			}
		}

		return hasDsp;
    }

    /* Return True if the target supports the MIPS MCU application specific extension.
     */
    public boolean supportsMcuAse() {
        // There's no way to tell from the MPLAB X API, but looking at datasheets for different PIC32
        // series suggests that devices that support microMIPS also support the MCU ASE.
        return supportsMicroMipsIsa();
    }

    /* Return True if the target supports the ARM instruction set.
     */
    public boolean supportsArmIsa() {
		TargetArch arch = getArch();

        return (TargetArch.ARMV7A == arch  ||  TargetArch.ARMV8A == arch);
    }

    /* Return True if the target supports the Thumb instruction set.
     */
    public boolean supportsThumbIsa() {
        return isArm();
    }

    /* Get the name of the FPU for ARM devices.  ARM devices have different FPU variants that can be
     * supported that determine whether it is single-precision only or also supports double-precision
     * as well as how many FPU registers are available and whether NEON SIMD extensions are supported.
     *
     * MIPS devices have only one FPU, so this will return an empty string for MIPS.
     */
    public String getArmFpuName() {
		String fpuName = "";

		if(isArm()  &&  hasFpu()) {
			switch (getArch()) {
                case ARMV7M:
                case ARMV7EM:
                    if(getCpuName().equals("cortex-m7"))
                        fpuName = "vfp5-dp-d16";
                    else
                        fpuName = "vfp4-sp-d16";
                    break;
                case ARMV7A:
                    // There does not yet seem to be a way to check for NEON other than name.
                    String name = getDeviceName();
                    if(name.startsWith("ATSAMA5D3"))
                        fpuName = "vfp4-dp-d16";
                    else
                        fpuName = "neon-vfpv4";
                    break;
                case ARMV8A:
                    fpuName = "fp-armv8";
                    break;
                default:
                    fpuName = "vfp4-sp-d16";
                    break;
            }
		}

        return fpuName;
    }

    /* Return a list of memory regions used by the target device.  This will return boot memory,
     * code memory, and data memory regions.
     */
    public List<LinkerMemoryRegion> getMemoryRegions() {
        if(null == lmrList_) {
            lmrList_ = new ArrayList<>();
            xMemoryPartition mainPartition = getPic().getMainPartition();

            // MIPS:  This is for the boot flash regions.  The CPU starts executing here at 0x1FC00000.
            // ARM:   This is for the SAM-BA boot ROM on Atmel devices, which seems to act as a simple 
            //        UART/USB bootloader and contains a routine for applications to program themselves.
            for(Node bootRegion : mainPartition.getBootConfigRegions()) {
                lmrList_.add(new LinkerMemoryRegion(bootRegion, LinkerMemoryRegion.Type.BOOT));
            }

            // This is the main code region and also includes the ITCM on ARM devices.
            for(Node codeRegion : mainPartition.getCodeRegions()) {
                lmrList_.add(new LinkerMemoryRegion(codeRegion, LinkerMemoryRegion.Type.CODE));
            }

            // This actually seems to be for RAM regions despite its name.
            // This includes the DTCM on ARM devices.
            for(Node gprRegion : mainPartition.getGPRRegions()) {
                lmrList_.add(new LinkerMemoryRegion(gprRegion, LinkerMemoryRegion.Type.SRAM));
            }

            // Used for the device's external bus interface, if present.
            for(Node ebiRegion : mainPartition.getEBIRegions()) {
                lmrList_.add(new LinkerMemoryRegion(ebiRegion, LinkerMemoryRegion.Type.EBI));
            }

            // Used for the device's serial quad interface, if present.
            for(Node sqiRegion : mainPartition.getSQIRegions()) {
                lmrList_.add(new LinkerMemoryRegion(sqiRegion, LinkerMemoryRegion.Type.SQI));
            }

            // Used for the device's external DDR or SDRAM interface, if present.
            for(Node ddrRegion : mainPartition.getDDRRegions()) {
                lmrList_.add(new LinkerMemoryRegion(ddrRegion, LinkerMemoryRegion.Type.SDRAM));
            }

            // Used for the device's config fuses.
            for(Node dcrRegion : mainPartition.getDCRRegions()) {
                lmrList_.add(new LinkerMemoryRegion(dcrRegion, LinkerMemoryRegion.Type.FUSE));
            }

            // Used for the device's peripheral registers.
            for(Node sfrRegion : mainPartition.getSFRRegions()) {
                lmrList_.add(new LinkerMemoryRegion(sfrRegion, LinkerMemoryRegion.Type.PERIPHERAL));
            }
        }

        return lmrList_;
    }

    /* Return a list of all of the special function registers (used for controlling peripherals) on
     * the device.  This does not include configuration registers; use getDCRs() for that.  This 
     * also does not include non-memory-mapped registers (NMMRs).
     */
    public List<SFR> getSFRs() {
        if(null == sfrList_) {
            sfrList_ = new ArrayList<>(64);
            List<Node> regions = getPic().getMainPartition().getSFRRegions();

            if(null == sfrOffsetMap_) {
                sfrOffsetMap_ = new HashMap<>();
            }

            for(Node sfrSection : regions) {
                NodeList childNodes = sfrSection.getChildNodes();
                long offset = getMagicOffsetForRegion(sfrSection);

                for(int i = 0; i < childNodes.getLength(); ++i) {
                    Node currentNode = childNodes.item(i);

                    if(currentNode.getNodeName().equals("edc:SFRDef")) {
                        SFR sfr = new SFR(currentNode);
                        sfrList_.add(sfr);

                        if(0 != offset) {
                            sfrOffsetMap_.put(sfr.getName(), offset);
                        }
                    }
                }
            }
        }

        return sfrList_;
    }

    /* Return a list of all of the device configuration registers on the device.  This does not 
     * include special function registers for peripherals; use getSFRs() for that.  This also does 
     * not include non-memory-mapped registers (NMMRs).
     */
    public List<DCR> getDCRs() {
        if(null == dcrList_) {
            dcrList_ = new ArrayList<>(16);
            List<Node> regions = getPic().getMainPartition().getDCRRegions();

            if(null == dcrOffsetMap_) {
                dcrOffsetMap_ = new HashMap<>();
            }

            for(Node dcrSection : regions) {
                NodeList childNodes = dcrSection.getChildNodes();
                long offset = getMagicOffsetForRegion(dcrSection);

                for(int i = 0; i < childNodes.getLength(); ++i) {
                    Node currentNode = childNodes.item(i);

                    if(currentNode.getNodeName().equals("edc:DCRDef")) {
                        DCR dcr = new DCR(currentNode);
                        dcrList_.add(dcr);

                        if(0 != offset) {
                            dcrOffsetMap_.put(dcr.getName(), offset);
                        }
                    }
                }
            }
        }

        return dcrList_;
    }

    /* Return a name that would be used in a linker script or "section" attribute for the section
     * of memory the DCR would occupy.
     */
    public String getDcrMemorySectionName(DCR dcr) {
        long addr = getRegisterAddress(dcr);

        if(isMips32()) {
            // Make into a kseg1 address.
            addr = (addr & 0x1FFFFFFFL) | 0xA0000000L;
        }

        return "config_" + String.format("%08X", addr);
    }

    /* Return a list of interrupts for this device.
     */
    public InterruptList getInterruptList() {
        if(null == interruptList_) {
            interruptList_ = new InterruptList(getPic());
        }

        return interruptList_;
    }

    /* Get the address at which the given special function register is located. This will return
     * whatever is in MPLAB X's database, which is the physical address on MIPS devices.
     */
    public long getRegisterAddress(SFR reg) {
        if(null == sfrOffsetMap_) {
            getSFRs();
        }

        long addr = reg.getAsLongElse("_addr", Long.valueOf(0)) + sfrOffsetMap_.getOrDefault(reg.getName(), 0L);
        return addr & 0xFFFFFFFFL;
    }

    /* Get the address at which the given device config register is located. This will return
     * whatever is in MPLAB X's database, which is the physical address on MIPS devices.
     */
    public long getRegisterAddress(DCR reg) {
        if(null == dcrOffsetMap_) {
            getDCRs();
        }

        long addr = reg.getAsLongElse("_addr", Long.valueOf(0)) + dcrOffsetMap_.getOrDefault(reg.getName(), 0L);
        return addr & 0xFFFFFFFFL;
    }


    /* Return the ATDF document relating to this target device or null if one could not be found.
     * ATDF documents came from Atmel, so not all MIPS-based devices have them (as of this writing
     * on 14 Nov 2019).  This will throw an SAXException if the file cannot be parsed properly or
     * a FileNotFound exception if the file cannot be opened for some reason.
     */
    public AtdfDoc getAtdfDocument() throws java.io.FileNotFoundException, SAXException {
        if(null == atdfDoc_) {
            try {
                atdfDoc_ = new AtdfDoc(name_);
            } catch(SAXException ex) {
                throw ex;
            } catch(Exception ex) {
                throw new java.io.FileNotFoundException(ex.getMessage());
            }
        }

        return atdfDoc_;
    }


    /* Ensure the device name is in a predictable format for use by users of this class.  We don't 
     * control what device names we get from the MPLAB X Device objects, so do this to ensure that 
     * we stay consistent even if they change.
     */
    private String normalizeDeviceName(String devname) {
        devname = devname.toUpperCase();

        if(devname.startsWith("SAM")) {
            devname = "AT" + devname;
        } else if(devname.startsWith("32")) {
            devname = "PIC" + devname;
        } else if(devname.startsWith("P32")) {
            devname = "PIC" + devname.substring(1);
        }

        return devname;        
    }

    /* It turns out that some regions (DCR regions in particular) can have what the .PIC files call
     * a "magic offset", which appears to be used to place unmapped registers into memory at some
     * fake memory region that MPLAB X knows how to deal with.  This method will check for said
     * offset by searching upward in the Node tree for the proper XML attribute.  This returns 0 if
     * no magic offset was found.
     */
    private long getMagicOffsetForRegion(Node region) {
        do {
            // We need the "edc:" prefix here because this is not part of the MPLAB X API.
            long offset = Utils.getNodeAttributeAsLong(region, "edc:magicoffset", 0L);
            if(0 != offset) {
                return offset;
            }
            region = region.getParentNode();
        } while(null != region);

        return 0L;
    }
}