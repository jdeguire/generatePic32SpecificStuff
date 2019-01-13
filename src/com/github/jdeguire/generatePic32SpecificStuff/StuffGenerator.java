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

                if(node.hasChildNodes())
                    nodeNames.addAll(getChildNodes(node, level+1));
            }
        }        
        return nodeNames;
    }

    public List<String> getMemoryRegionsForLinker(Device device) 
             throws Anomaly, SAXException, IOException, ParserConfigurationException {
        TargetDevice target = new TargetDevice(device.getName());
        xPIC xpic = target.getPic();
        
        ArrayList<String> regions = new ArrayList<>();

        // TODO: Can we get this same info from xpic.getMainPartition().blahblah()?, such as
        //       getInstRegions()?  There's ones for DCRs, but what about NMMRs and RAM?
        
        if(target.isArm()) {
            // The ARM devices have their memory regions in different "edc::___Sector" nodes, so just
            // look for the ones we care about (ie. that Microchip's XC32 uses in its linker scripts).

            Node physicalSpaceNode = xpic.first("PhysicalSpace");
            NodeList physicalSpaces = physicalSpaceNode.getChildNodes();

            for(int i = 0; i < physicalSpaces.getLength(); ++i) {
                Node space = physicalSpaces.item(i);
                
                switch(space.getNodeName()) {
                    case "edc:CodeSector":
                    {
                        NamedNodeMap attrs = space.getAttributes();
                        String regionId = attrs.getNamedItem("edc:regionid").getNodeValue();
                        String beginAddr = attrs.getNamedItem("edc:beginaddr").getNodeValue();
                        String endAddr = attrs.getNamedItem("edc:endaddr").getNodeValue();

                        String length = Long.toHexString(Long.parseLong(endAddr) - Long.parseLong(beginAddr));

                        switch(regionId) {
                            case "IFLASH":
                                regions.add("rom (rx) : ORIGIN = " + beginAddr + ", LENGTH = " + length);
                                break;
                            case "ITCM":
                                regions.add("itcm (rwx) : ORIGIN = " + beginAddr + ", LENGTH = " + length);
                                break;
                            default:
                                break;
                        }
                        break;
                    }
                    case "edc:GPRDataSector":
                    {
                        NamedNodeMap attrs = space.getAttributes();
                        String regionId = attrs.getNamedItem("edc:regionid").getNodeValue();
                        String beginAddr = attrs.getNamedItem("edc:beginaddr").getNodeValue();
                        String endAddr = attrs.getNamedItem("edc:beginaddr").getNodeValue();

                        String length = Long.toHexString(Long.parseLong(endAddr) - Long.parseLong(beginAddr));

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
                        break;
                    }
                    default:
                        break;
                }
                    
            }
        } else {
            regions.add("Comong soon!");
        }

        return regions;
    }
}
