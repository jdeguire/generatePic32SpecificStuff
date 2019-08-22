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

import com.microchip.crownking.edc.DCR;
import com.microchip.crownking.edc.Register;
import com.microchip.crownking.edc.SFR;
import com.microchip.crownking.mplabinfo.FamilyDefinitions.Family;
import com.microchip.crownking.mplabinfo.FamilyDefinitions.SubFamily;
import com.microchip.mplab.crownkingx.xMemoryPartition;
import com.microchip.mplab.crownkingx.xPICFactory;
import com.microchip.mplab.crownkingx.xPIC;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


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
    private static HashMap<String, String> atdfCache_ = new HashMap<>(100);

    /* Create a new TargetDevice based on the given name.  Throws an exception if the given name is
     * not recognized by this class.  Note that this class parses the name just enough to determine
     * the device's family, so a lack of an exception does not necessarily mean that the device is 
     * fully supported.
     */
    TargetDevice(String devname) throws com.microchip.crownking.Anomaly, 
		org.xml.sax.SAXException,
		java.io.IOException, 
		javax.xml.parsers.ParserConfigurationException, 
		IllegalArgumentException {

        name_ = devname.toUpperCase();
        pic_ = (xPIC)xPICFactory.getInstance().get(name_);
        
		if(Family.PIC32 == pic_.getFamily()  ||  Family.ARM32BIT == pic_.getFamily()) {
   			instructionSets_ = new ArrayList<>(pic_.getInstructionSet().getSubsetIDs());

			String setId = pic_.getInstructionSet().getID();
			if(setId != null  &&  !setId.isEmpty())
				instructionSets_.add(setId);
        }
		else {
            String what = "Device " + devname + " is not a recognized MIPS32 or ARM device.";
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

    /* Get the name of the device provided to the constructor of this class, but in all uppercase.
     */
    public String getDeviceName() {
        return name_;
    }

    public String getDeviceNameMacro() {
        String name = getDeviceName();
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
                        //        and ARMV7-EM, so we'll do it here.
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
        return pic_.hasFPU();
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
                    if(name.startsWith("SAMA5D3")  ||  name.startsWith("ATSAMA5D3"))
                        fpuName = "vfp4-dp-d16";
                    else
                        fpuName = "neon-vfpv4";
                    break;
                default:
                    fpuName = "vfp4-dp-d16";
                    break;
            }
		}

        return fpuName;
    }

    /* Return a list of memory regions used by the target device.  This will return boot memory,
     * code memory, and data memory regions.
     */
    public List<LinkerMemoryRegion> getMemoryRegions() {
        ArrayList<LinkerMemoryRegion> regions = new ArrayList<>();
        xMemoryPartition mainPartition = getPic().getMainPartition();

        // MIPS:  This is for the boot flash regions.  The CPU starts executing here at 0x1FC00000.
        // ARM:   This is for the SAM-BA boot ROM on Atmel devices, which seems to act as a simple 
        //        UART/USB bootloader and contains a routine for applications to program themselves.
        for(Node bootRegion : mainPartition.getBootConfigRegions()) {
            regions.add(new LinkerMemoryRegion(bootRegion, LinkerMemoryRegion.Type.BOOT));
        }

        // This is the main code region and also includes the ITCM on ARM devices.
        for(Node codeRegion : mainPartition.getCodeRegions()) {
            regions.add(new LinkerMemoryRegion(codeRegion, LinkerMemoryRegion.Type.CODE));
        }

        // This actually seems to be for RAM regions despite its name.
        // This includes the DTCM on ARM devices.
        for(Node gprRegion : mainPartition.getGPRRegions()) {
            regions.add(new LinkerMemoryRegion(gprRegion, LinkerMemoryRegion.Type.SRAM));
        }

        // Used for the device's external bus interface, if present.
        for(Node ebiRegion : mainPartition.getEBIRegions()) {
            regions.add(new LinkerMemoryRegion(ebiRegion, LinkerMemoryRegion.Type.EBI));
        }

        // Used for the device's serial quad interface, if present.
        for(Node sqiRegion : mainPartition.getSQIRegions()) {
            regions.add(new LinkerMemoryRegion(sqiRegion, LinkerMemoryRegion.Type.SQI));
        }

        // Used for the device's external DDR or SDRAM interface, if present.
        for(Node ddrRegion : mainPartition.getDDRRegions()) {
            regions.add(new LinkerMemoryRegion(ddrRegion, LinkerMemoryRegion.Type.SDRAM));
        }
        
        return regions;
    }

    /* Return a list of all of the special function registers (used for controlling peripherals) on
     * the device.  This does not include configuration registers; use getDCRs() for that.  This 
     * also does not include non-memory-mapped registers (NMMRs).
     */
    public List<SFR> getSFRs() {
        ArrayList<SFR> sfrList = new ArrayList<>(256);

        for(Node sfrSection : getPic().getMainPartition().getSFRRegions()) {
            NodeList childNodes = sfrSection.getChildNodes();

            for(int i = 0; i < childNodes.getLength(); ++i) {
                Node currentNode = childNodes.item(i);

                if(currentNode.getNodeName().equals("edc:SFRDef")) {
                    sfrList.add(new SFR(currentNode));
                }
            }
        }

        return sfrList;
    }

    /* Return a list of all of the device configuration registers on the device.  This does not 
     * include special function registers for peripherals; use getSFRs() for that.  This also does 
     * not include non-memory-mapped registers (NMMRs).
     */
    public List<DCR> getDCRs() {
        ArrayList<DCR> dcrList = new ArrayList<>(256);

        for(Node dcrSection : getPic().getMainPartition().getDCRRegions()) {
            NodeList childNodes = dcrSection.getChildNodes();

            for(int i = 0; i < childNodes.getLength(); ++i) {
                Node currentNode = childNodes.item(i);

                if(currentNode.getNodeName().equals("edc:DCRDef")) {
                    dcrList.add(new DCR(currentNode));
                }
            }
        }

        return dcrList;
    }

    /* Get the address at which the given register is located.  Registers include SFRs and DCRs, so
     * objects retrieved with getSFRs() and getDCRs() can be used with this method.  This will return
     * whatever is in MPLAB X's database, which is the physical address on MIPS devices.
     */
    public long getRegisterAddress(Register reg) {
        long addr = reg.getAsLongElse("_addr", Long.valueOf(0));
        return addr & 0xFFFFFFFF;
    }


    /*******
     * Atmel ATDF file stuff below here.  This may get moved to its own file at some point, but keep
     * it here for now.
     */
    public class AtdfParameter{
        public String name;
        public String value;
        public String caption;
        
        AtdfParameter(Node atdfNode) {
            NamedNodeMap attrs = atdfNode.getAttributes();

            Node nameNode = attrs.getNamedItem("name");
            Node valueNode = attrs.getNamedItem("value");
            Node captionNode = attrs.getNamedItem("caption");

            if(null == nameNode)
                name = "";
            else
                name = nameNode.getNodeValue();

            if(null == valueNode)
                value = "";
            else
                value = valueNode.getNodeValue();

           if(null == captionNode)
                caption = "";
            else
                caption = captionNode.getNodeValue();
        }
    }

    private void populateAtdfCache() {
        File packsdir = new File(System.getProperty("packslib.packsfolder"));
        String exts[] = {"atdf", "ATDF"};
        Collection<File> atdfFiles = FileUtils.listFiles(packsdir, exts, true);

        for(File f : atdfFiles) {
            String basename = f.getName();
            basename = basename.substring(0, basename.lastIndexOf('.'));

            atdfCache_.put(basename, f.getAbsolutePath());
        }
    }

    public Document getAtdfDocument() {
        Document atdfDoc = null;

        try {
            if(atdfCache_.isEmpty())
                populateAtdfCache();

            String devname = name_;
            if(devname.startsWith("SAM"))
                devname = "AT" + devname;

            String atdfPath = atdfCache_.get(devname);

            // Based on example code from:
            // https://www.mkyong.com/java/how-to-read-xml-file-in-java-dom-parser/
            if(null != atdfPath) {
                File atdfFile = new File(atdfPath);

                DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                atdfDoc = docBuilder.parse(atdfFile);
                atdfDoc.getDocumentElement().normalize();
            }
        } catch(Exception e) {
            atdfDoc = null;
        }

        return atdfDoc;
    }

    public List<AtdfParameter> getAtdfParameters(Document atdfDoc, String peripheral) {
        ArrayList<AtdfParameter> params = new ArrayList<>(16);
        Element atdfElement = atdfDoc.getDocumentElement();

        // Get to the "<device>" node, which is under the "<devices>" node.
        Node devicesNode = Utils.filterFirstChildNode((Node)atdfElement, "devices", null, null);
        Node deviceNode = Utils.filterFirstChildNode(devicesNode, "device", "name", name_);
        Node targetNode;

        // Navigate to peripheral node if a peripheral is provided.
        if(null != peripheral  &&  !peripheral.isEmpty()) {
            // We need to search for the peripheral's basename--that is, a peripheral name minus
            // the instance number.  So "ADC1" would have a basename of "ADC".
            int basesplit = peripheral.length()-1;
            while(basesplit > 0  &&  Character.isDigit(peripheral.charAt(basesplit)))
                --basesplit;

            String basename = peripheral.substring(0, basesplit+1);
            Node peripheralsNode = Utils.filterFirstChildNode(deviceNode, "peripherals", null, null);
            Node moduleNode = Utils.filterFirstChildNode(peripheralsNode, "module", "name", basename);

            targetNode = Utils.filterFirstChildNode(moduleNode, "instance", "name", peripheral);
        } else {
            // Else we'll get the parameters for the device itself.
            targetNode = deviceNode;
        }

        Node parametersNode = Utils.filterFirstChildNode(targetNode, "parameters", null, null);
        if(null != parametersNode) {
            List<Node> paramsList = Utils.filterAllChildNodes(parametersNode, "param", null, null);

            for(Node paramNode : paramsList) {
                params.add(new AtdfParameter(paramNode));
            }
        }

        return params;
    }
}