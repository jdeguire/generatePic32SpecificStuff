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
import com.microchip.crownking.Pair;
import java.util.List;
import java.util.ArrayList;

import com.microchip.crownking.mplabinfo.DeviceSupport;
import com.microchip.crownking.mplabinfo.DeviceSupport.Device;
import com.microchip.crownking.mplabinfo.DeviceSupportException;
import com.microchip.crownking.mplabinfo.FamilyDefinitions;
import com.microchip.crownking.mplabinfo.FamilyDefinitions.Family;
import com.microchip.crownking.mplabinfo.FamilyDefinitions.SubFamily;
import com.microchip.mplab.crownkingx.xMemoryPartition;
import com.microchip.mplab.crownkingx.xPICFactory;
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

    private String outputDirBase_;
    
    /**
     * Constructor for the Stuff Generator.
     * 
     * @param outputDir    The base path for generated files.  The subclasses will add on to this
     *                     as needed to separate by header vs linker script and will add the device
     *                     name.
     */
    public StuffGenerator(String outputDir) {
        outputDirBase_ = outputDir;
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
    }

    /**
     * Get the map of XML nodes used to provide information about the given device from the MPLAB X
     * device database.
     */
    public List<String> makeNodeMap(Device device) 
            throws Anomaly, SAXException, IOException, ParserConfigurationException {
        TargetDevice target = new TargetDevice(device.getName());

        List<Node> childNodes = target.getPic().children();
        ArrayList<String> nodeNames = new ArrayList<>(childNodes.size());

        for(Node node : childNodes) {
            String name = node.getNodeName();

            if('#' != name.charAt(0)) {
                if(node.getNodeValue() != null)
                    name += "=" + node.getNodeValue();

                if(node.hasAttributes()) {
                    NamedNodeMap attributes = node.getAttributes();

                    name += "  [" + attributes.item(0).getNodeName() + "=" + attributes.item(0).getNodeValue();

                    for(int i = 1; i < attributes.getLength(); ++i) {
                        name += ", " + attributes.item(i).getNodeName() + "=" + attributes.item(i).getNodeValue();
                    }

                    name += "]";
                }

                nodeNames.add(name);

                if(node.hasChildNodes())
                    nodeNames.addAll(getChildNodes(node, 1));
            }
        }

        return nodeNames;
    }

    /**
     * This is a helper method that will recursively get children of the given node.
     * 
     * @param node
     * @param level
     * @return 
     */
    private ArrayList<String> getChildNodes(Node parentNode, int level) {
        NodeList childNodes = parentNode.getChildNodes();
        ArrayList<String> nodeNames = new ArrayList<>(childNodes.getLength());

        for(int i = 0; i < childNodes.getLength(); ++i) {
            Node node = childNodes.item(i);

            if('#' != node.getNodeName().charAt(0)) {
                String name = "";

                for(int j = 0; j < level; ++j)
                    name += "--";

                name += node.getNodeName();

                if(node.getNodeValue() != null)
                    name += "=" + node.getNodeValue();

                if(node.hasAttributes()) {
                    NamedNodeMap attributes = node.getAttributes();

                    name += "  [" + attributes.item(0).getNodeName() + "=" + attributes.item(0).getNodeValue();

                    for(int k = 1; k < attributes.getLength(); ++k) {
                        name += ", " + attributes.item(k).getNodeName() + "=" + attributes.item(k).getNodeValue();
                    }

                    name += "]";
                }

                nodeNames.add(name);

                if(node.hasChildNodes() &&  level >= 0)
                    nodeNames.addAll(getChildNodes(node, level+1));
            }
        }        
        return nodeNames;
    }

    private ArrayList<String> getChildNodes(List<Node> childNodes, int level) {
        ArrayList<String> nodeNames = new ArrayList<>(childNodes.size());

        for(Node node : childNodes) {
            if('#' != node.getNodeName().charAt(0)) {
                String name = "";

                for(int j = 0; j < level; ++j)
                    name += "--";

                name += node.getNodeName();

                if(node.getNodeValue() != null)
                    name += "=" + node.getNodeValue();

                if(node.hasAttributes()) {
                    NamedNodeMap attributes = node.getAttributes();

                    name += "  [" + attributes.item(0).getNodeName() + "=" + attributes.item(0).getNodeValue();

                    for(int k = 1; k < attributes.getLength(); ++k) {
                        name += ", " + attributes.item(k).getNodeName() + "=" + attributes.item(k).getNodeValue();
                    }

                    name += "]";
                }

                nodeNames.add(name);

                if(node.hasChildNodes() &&  level >= 0)
                    nodeNames.addAll(getChildNodes(node, level+1));
            }
        }
        return nodeNames;
    }

    public List<String> getMemorySpaces(Device device)
             throws Anomaly, SAXException, IOException, ParserConfigurationException {
        TargetDevice target = new TargetDevice(device.getName());
        xPIC xpic = target.getPic();
        xMemoryPartition mainPartition = xpic.getMainPartition();

        ArrayList<String> spaces = new ArrayList<>();
        Pair<Long, Long> temp;
        
        temp = mainPartition.getBootConfigRange();
        if(temp != null) {
            spaces.add("Boot Cfg Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getBootConfigRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getBootRAMRange();
        if(temp != null) {
            spaces.add("Boot RAM Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getCodeRange();
        if(temp != null) {
            spaces.add("Code Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getCodeRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getDCIRange();
        if(temp != null) {
            spaces.add("DCI Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getDCIRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getDCRRange();
        if(temp != null) {
            spaces.add("DCR Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getDCRRegions(), -1));
            spaces.add(System.lineSeparator());
        }
        
        temp = mainPartition.getDDRRange();
        if(temp != null) {
            spaces.add("DDR Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getDDRRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getDIARange();
        if(temp != null) {
            spaces.add("DIA Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getDIARegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getDPRRange();
        if(temp != null) {
            spaces.add("DPR Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getDPRRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getDataFlashSpaceRange();
        if(temp != null) {
            spaces.add("Data Flash Space Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getDeviceIDRange();
        if(temp != null) {
            spaces.add("Device ID Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getEBIRange();
        if(temp != null) {
            spaces.add("EBI Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getEBIRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getExternalCodeRange();
        if(temp != null) {
            spaces.add("External Code Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getExternalCodeRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getFileRange();
        if(temp != null) {
            spaces.add("File Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getFileRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getFlashDataRange();
        if(temp != null) {
            spaces.add("Flash Data Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getGPRRange();
        if(temp != null) { 
            spaces.add("GPR Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getGPRRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getInstRange();
        if(temp != null) {
            spaces.add("Inst Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getInstRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getLinearDataRange();
        if(temp != null) {
            spaces.add("Linear Data Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getLinearRange();
        if(temp != null) {
            spaces.add("Linear Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getLinearizedRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        spaces.add("NMMR Region: ");
        spaces.addAll(getChildNodes(mainPartition.getNMMRPlaces(), -1));
        spaces.add(System.lineSeparator());

        temp = mainPartition.getSFRRange();
        if(temp != null) {
            spaces.add("SFR Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getSFRRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getSPIFlashRange();
        if(temp != null) { 
            spaces.add("SPI Flash Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getSPIFlashRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        temp = mainPartition.getSQIRange();
        if(temp != null ) {
            spaces.add("SQI Region: " + Long.toHexString(temp.first)
                        + " -> " + Long.toHexString(temp.second));
            spaces.addAll(getChildNodes(mainPartition.getSQIRegions(), -1));
            spaces.add(System.lineSeparator());
        }

        return spaces;
    }    
    
    public List<String> getMemoryRegionsForLinker(Device device) 
             throws Anomaly, SAXException, IOException, ParserConfigurationException {
        TargetDevice target = new TargetDevice(device.getName());
        xPIC xpic = target.getPic();
        xMemoryPartition mainPartition = xpic.getMainPartition();

        ArrayList<String> regions = new ArrayList<>();

        if(target.isArm()) {
            for(Node codeRegion : mainPartition.getCodeRegions()) {
                NamedNodeMap attrs = codeRegion.getAttributes();

                String regionId = attrs.getNamedItem("edc:regionid").getNodeValue();
                String beginAddr = attrs.getNamedItem("edc:beginaddr").getNodeValue();
                String endAddr = attrs.getNamedItem("edc:endaddr").getNodeValue();

                String length = "0x" + Integer.toHexString(Integer.decode(endAddr) - Integer.decode(beginAddr));

                switch(regionId) {
                    case "IFLASH":
                        regions.add("rom (rx) : ORIGIN = " + beginAddr + ", LENGTH = " + length);
                        break;
                    case "ITCM":
                        // TODO:  This is incorrect in the database as it is the same size as the flash.
                        //        Is there a reason for this and a workaround?
                        regions.add("itcm (rwx) : ORIGIN = " + beginAddr + ", LENGTH = " + length);
                        break;
                    default:
                        break;
                }
            }

            for(Node gprRegion : mainPartition.getGPRRegions()) {
                NamedNodeMap attrs = gprRegion.getAttributes();
                String regionId = attrs.getNamedItem("edc:regionid").getNodeValue();
                String beginAddr = attrs.getNamedItem("edc:beginaddr").getNodeValue();
                String endAddr = attrs.getNamedItem("edc:endaddr").getNodeValue();

                String length = "0x" + Integer.toHexString(Integer.decode(endAddr) - Integer.decode(beginAddr));

                switch(regionId) {
                    case "IRAM":
                        regions.add("ram (rwx) : ORIGIN = " + beginAddr + ", LENGTH = " + length);
                        break;
                    case "DTCM":
                        regions.add("dtcm (rwx) : ORIGIN = " + beginAddr + ", LENGTH = " + length);
                        break;
                    default:
                        break;
                }
            }
        } else {   // MIPS32
            for(Node bootRegion : mainPartition.getBootConfigRegions()) {
                NamedNodeMap attrs = bootRegion.getAttributes();

                String regionId = attrs.getNamedItem("edc:regionid").getNodeValue();
                String beginAddr = attrs.getNamedItem("edc:beginaddr").getNodeValue();
                String endAddr = attrs.getNamedItem("edc:endaddr").getNodeValue();

                String length = "0x" + Integer.toHexString(Integer.decode(endAddr) - Integer.decode(beginAddr));

                // Make this a kseg1 address.
                beginAddr = "0x" + Integer.toHexString(Integer.decode(beginAddr) | 0xa0000000);
                
                regions.add(regionId + " (rx) : ORIGIN = " + beginAddr + ", LENGTH = " + length);
            }

            for(Node codeRegion : mainPartition.getCodeRegions()) {
                NamedNodeMap attrs = codeRegion.getAttributes();

                String regionId = attrs.getNamedItem("edc:regionid").getNodeValue();
                String beginAddr = attrs.getNamedItem("edc:beginaddr").getNodeValue();
                String endAddr = attrs.getNamedItem("edc:endaddr").getNodeValue();

                String length = "0x" + Integer.toHexString(Integer.decode(endAddr) - Integer.decode(beginAddr));

                if(regionId.equals("code")) {
                    // Make this a kseg0 address.
                    beginAddr = "0x" + Integer.toHexString(Integer.decode(beginAddr) | 0x80000000);
                    regions.add("kseg0_program_mem (rx) : ORIGIN = " + beginAddr + ", LENGTH = " + length);                    
                }
            }

            for(Node gprRegion : mainPartition.getGPRRegions()) {
                NamedNodeMap attrs = gprRegion.getAttributes();
                String regionId = attrs.getNamedItem("edc:regionid").getNodeValue();
                String beginAddr = attrs.getNamedItem("edc:beginaddr").getNodeValue();
                String endAddr = attrs.getNamedItem("edc:endaddr").getNodeValue();

                String length = "0x" + Integer.toHexString(Integer.decode(endAddr) - Integer.decode(beginAddr));

                switch(regionId) {
                    case "kseg0_data_mem":
                        beginAddr = "0x" + Integer.toHexString(Integer.decode(beginAddr) | 0x80000000);
                        regions.add(regionId + " (rwx) : ORIGIN = " + beginAddr + ", LENGTH = " + length);
                        break;
                    case "kseg1_data_mem":
                        beginAddr = "0x" + Integer.toHexString(Integer.decode(beginAddr) | 0xa0000000);
                        regions.add(regionId + " (rwx) : ORIGIN = " + beginAddr + ", LENGTH = " + length);
                        break;
                    default:
                        break;
                }
            }
        }

        return regions;
    }
}
