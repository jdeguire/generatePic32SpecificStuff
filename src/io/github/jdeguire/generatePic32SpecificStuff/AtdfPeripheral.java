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

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * This represents a peripheral on an Atmel device as read from its corresponding ATDF file.  This
 * will encapsulate the peripheral's registers and information about it, such as the number of
 * instances and its unique ID.
 * 
 * Each peripheral is made of one or more instances, such as SPI0 and SPI1 being two instances of
 * the SPI peripheral.  This object will contain objects for the instances.  Each peripheral also
 * has a unique ID that differentiates it from other peripherals.  For example, the ADC peripheral 
 * on the SAMA5 has a different ID than the ADC on the SAME54 because they have different 
 * implementations.
 */
public class AtdfPeripheral {
    /* We need two Nodes to describe a peripheral for a given device:  one to describe its instances
     * as used on this particular device and the other to describe the peripheral's registers.
     */
    private final Node moduleInstancesNode_;
    private final Node moduleRegistersNode_;
    private final ArrayList<AtdfInstance> instances_ = new ArrayList<>(8);
    private final ArrayList<AtdfRegisterGroup> groups_ = new ArrayList<>(4);


    /* Create a new AtdfPeripheral for the peripheral of the given name by searching the ATDF 
     * document represented by the given Node for the needed info.  The Node must be the root 
     * element of an ATDF document.  This is handled in the AtdfDoc class, so use methods in
     * there to get an AtdfPeripheral object instead of calling this directly.
     */
    public AtdfPeripheral(Node atdfRootNode, String periphName) throws SAXException {
        Node devicesNode = Utils.filterFirstChildNode(atdfRootNode, "devices", null, null);
        Node deviceNode = Utils.filterFirstChildNode(devicesNode, "device", null, null);
        Node peripheralsNode = Utils.filterFirstChildNode(deviceNode, "peripherals", null, null);
        moduleInstancesNode_ = Utils.filterFirstChildNode(peripheralsNode, "module", "name", periphName);

        Node modulesNode = Utils.filterFirstChildNode(atdfRootNode, "modules", null, null);
        moduleRegistersNode_ = Utils.filterFirstChildNode(modulesNode, "module", "name", periphName);

        if(null == moduleInstancesNode_  ||  null == moduleRegistersNode_) {
            throw new SAXException("Module XML nodes not found when creating an AtdfPeripheral for " +
                                   "peripheral " + periphName +
                                   ".  The given Node needs to be the root of an AtdfDoc.");
        }
    }

    /* This is a private constructor used by the getAllPeripherals() method to directly set the 
     * object's members without any checking.
     */
    private AtdfPeripheral(Node instancesNode, Node registersNode) {
        moduleInstancesNode_ = instancesNode;
        moduleRegistersNode_ = registersNode;
    }


    /* Get the name of the peripheral to which this object applies.
     */
    public String getName() {
        return Utils.getNodeAttribute(moduleInstancesNode_, "name", "");
    }

    /* Get descriptive text for the peripheral.
     */
    public String getCaption() {
        return Utils.getNodeAttribute(moduleRegistersNode_, "caption", "");
    }

    /* Get a unique ID string for this peripheral.  Two peripherals with the same name will have
     * different IDs if they have different implementations.  For example, the ADC on the SAME54 has
     * a different ID from the ADC on the SAMA5D27.  Returns an empty string if no ID is provided.
     */
    public String getModuleId() {
        return Utils.getNodeAttribute(moduleInstancesNode_, "id", "");
    }

    /* Get a string representing the version of this peripheral.  Versions appear to come in two 
     * flavors:  either a three-place decimal value (eg. "1.0.2") or a series of letters (eg. "B" or
     * "ZJ").  Returns an empty string if no version is provided.
     */
    public String getModuleVersion() {
        return Utils.getNodeAttribute(moduleInstancesNode_, "version", "");
    }


    /* Get a list of all of the instances for this peripheral.  This throws an SAXException if an
     * instance could not be read from the ATDF docuemnt, which indicates a corrupted document or
     * an odd peripheral (the FUSES peripheral is like this because it has no "base" or "root" 
     * register group that starts the peripherals address space).
     */
    public List<AtdfInstance> getAllInstances() throws SAXException {
        if(instances_.isEmpty()) {
            List<Node> instanceList = Utils.filterAllChildNodes(moduleInstancesNode_, "instance", null, null);

            for(Node instNode : instanceList) {
                instances_.add(new AtdfInstance(instNode));
            }
        }

        return instances_;
    }

    /* Get a single instance as denoted by the index.  The order of instances is dependent on their
     * order in the ATDF document, but are generally logical.  That is, calling this on an ADC with
     * index 0 can be assumed to return an instance for "ADC0", index 1 returns an instance for 
     * "ADC1", and so on.
     *
     * This can throw an SAXException because this calls getAllInstances() to find the instances if 
     * they haven't yet been found.
     */
    public AtdfInstance getInstance(int index) throws SAXException {
        return getAllInstances().get(index);
    }

    /* Get a list of all register groups for this peripheral.
     */
    public List<AtdfRegisterGroup> getAllRegisterGroups() {
        if(groups_.isEmpty()) {
            List<Node> groupList = Utils.filterAllChildNodes(moduleRegistersNode_, "register-group", null, null);

            for(Node groupNode : groupList) {
                groups_.add(new AtdfRegisterGroup(moduleRegistersNode_, groupNode));
            }
        }

        return groups_;
    }

    /* Get a single register group as denoted by the index.
     */
    public AtdfRegisterGroup getRegisterGroupByIndex(int index) {
        return getAllRegisterGroups().get(index);
    }

    /* Get a group by name or null if the group is not found.  This uses the name as found in the 
     * ATDF document, which is what is returned by AtdfRegisterGroup::getName().
     */
    public AtdfRegisterGroup getRegisterGroupByName(String name) {
        for(AtdfRegisterGroup group : getAllRegisterGroups()) {
            if(name.equals(group.getName())) {
                return group;
            }
        }

        return null;
    }

    /* Search through the ATDF document representd by the given Node for all of the peripherals on
     * the device represented by the document and return a list of them.  This will return an empty
     * list if the Node does not represent a valid ATDF file.  The Node must be the root node of the
     * file.  This is handled in the AtdfDoc class, so use methods in there to get peripherals rather
     * than calling this directly.
     *
     * We do this using a static method instead of just having the AtdfDoc class walk the Nodes
     * because here we need to find and match up two Nodes in two different locations in the file
     * in order to properly represent a peripheral.  Using this static method should make this
     * process a bit faster than trying to call the normal constructor over and over again.
     */
    public static List<AtdfPeripheral> getAllPeripherals(Node atdfRootNode) {
        ArrayList<AtdfPeripheral> peripherals = new ArrayList<>(20);

        Node devicesNode = Utils.filterFirstChildNode(atdfRootNode, "devices", null, null);
        Node deviceNode = Utils.filterFirstChildNode(devicesNode, "device", null, null);
        Node peripheralsNode = Utils.filterFirstChildNode(deviceNode, "peripherals", null, null);        
        Node modulesNode = Utils.filterFirstChildNode(atdfRootNode, "modules", null, null);        

        List<Node> instanceNodes = Utils.filterAllChildNodes(peripheralsNode, "module", "name", null);
        for(Node instNode : instanceNodes) {
            String periphName = Utils.getNodeAttribute(instNode, "name", "");
            Node regNode = Utils.filterFirstChildNode(modulesNode, "module", "name", periphName);

            if(null != regNode) {
                peripherals.add(new AtdfPeripheral(instNode, regNode));
            }
        }

        return peripherals;
    }
}