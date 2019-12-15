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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Peripheral registers are organized into groups to allow for a "master group" and "sub-groups".
 * Most peripherals have only one group, but a few have multiple groups.  For example, the DMA
 * Controller peripheral on some devices has a master group that contains registers to control the
 * whole peripheral, but also has subgroups to control the DMA channels individually.
 */
public class AtdfRegisterGroup {
    private final Node moduleNode_;
    private final Node groupNode_;
    private final HashMap<String, ArrayList<AtdfRegister>> members_ = new HashMap<>(10);
    
    /* Create a new AtdfRegisterGroup based on the given nodes from an ATDF document.  The 
     * 'moduleNode' is a Node that refers to the "module" XML node that contains the desired group
     * indicated by 'groupNode'.  This is handled in the AtdfPeripheral class, so use methods in
     * there to get an AtdfRegisterGroup object for that peripheral instead of calling this directly.
     */
    public AtdfRegisterGroup(Node moduleNode, Node groupNode) {
        moduleNode_ = moduleNode;
        groupNode_ = groupNode;
    }

    /* Get the group name as it appears in the ATDF document.
     */
    public String getName() {
        return Utils.getNodeAttribute(groupNode_, "name", "");
    }

    /* Get the name of the peripheral that owns this group.
     */
    public String getOwningPeripheralName() {
        return Utils.getNodeAttribute(moduleNode_, "name", "");        
    }

    /* Get descriptive text for the peripheral.
     */
    public String getCaption() {
        return Utils.getNodeAttribute(groupNode_, "caption", "");
    }

    /* Get the alignment of this group, which indicates the byte boundary upon which the group must
     * start.  Returns 0 if no special alignment is required.
     */
    public int getAlignment() {
        int alignment = Utils.getNodeAttributeAsInt(groupNode_, "aligned", 0);

        if(alignment < 0)
            alignment = 0;

        return alignment;
    }

    /* Get the memory section of this register group that could be used in GCC "section" attributes.
     * This will return an empty string if this group does not have a special section (which will
     * be most of them).
     */
    public String getMemorySection() {
        return Utils.getNodeAttribute(groupNode_, "section", "");
    }

    /* Get the size in bytes of this group, which may indicate that padding bytes are needed at the 
     * end of the group if this value is larger than the combined sizes of the registers in the 
     * group.  Returns 0 if no special size or padding is required.
     */
    public int getSizeInBytes() {
        int size = Utils.getNodeAttributeAsInt(groupNode_, "size", 0);

        if(size < 0)
            size = 0;

        return size;
    }

    /* Return the names of the modes the members of this register group use.  A mode is basically a
     * variant that allows registers to take on a different role.  For example, the SERCOM peripheral
     * has modes for SPI, I2C, and USART because the SERCOM peripheral can act as all three and how
     * the registers works depends on the protocol in use.  The default mode name is "DEFAULT".
     */
    public List<String> getMemberModes() {
        if(members_.isEmpty()) {
            populateMemberMap();
        }

        return new ArrayList<>(members_.keySet());
    }

    /* Return the registers that are a part of the given mode or an empty List if the given mode 
     * name does not exist.
     */
    public List<AtdfRegister> getMembersByMode(String modeName) {
        if(members_.isEmpty()) {
            populateMemberMap();
        }

        if(members_.containsKey(modeName)) {
            return members_.get(modeName);
        } else {
            return Collections.<AtdfRegister>emptyList();
        }
    }

    /* Get a list of all of the members of this group across all modes.  Use 
     * AtdfRegister::isGroupAlias() to check if the member is a normal register (false) or an alias 
     * for a subgroup of this group (true).
     */
    public List<AtdfRegister> getAllMembers() {
        if(members_.isEmpty()) {
            populateMemberMap();
        }

        ArrayList<AtdfRegister> allMembers = new ArrayList<>(32);

        for(ArrayList<AtdfRegister> modeMembers : members_.values()) {
            allMembers.addAll(modeMembers);
        }

        return allMembers;
    }


    /* Populate our member map such that the keys are the modes of the registers and the values are
     * lists of the registers that are applicable in that mode.  A mode refers to the operating mode
     * of the peripheral.  A good example of this is the SERCOM peripheral, which has SPI, I2C, and
     * USART modes.
     */
    private void populateMemberMap() {
        NodeList children = groupNode_.getChildNodes();
        List<String> possibleModes = getPossibleMemberModes(children);
        List<ArrayList<AtdfRegister>> possibleRegisters = 
                    getPossibleMemberRegistersByMode(possibleModes, children);

        /* The original Atmel headers appear to coalesce modes that had the same registers, so we
         * need to do the same by comparing all of the registers in each mode and making a single
         * mode from multiple equivalent modes.
         */
        if(possibleModes.size() > 1) {
            for(int i = 0; i < possibleModes.size(); ++i) {
                for(int j = possibleModes.size()-1; j > i; --j) {
                    // Walk backwards so we can remove elements without having to worry about 
                    // invalidating our iteration.

                    if(areRegistersEqual(possibleRegisters.get(i), possibleRegisters.get(j))) {
                        // Modes are equal because their registers are equal, so remove the duplicate
                        // unless they don't share any part of their name.
                        String prefix = getCommonPrefix(possibleModes.get(i), possibleModes.get(j));

                        if(!prefix.isEmpty()) {
                            possibleModes.set(i, prefix);
                            possibleModes.remove(j);
                            possibleRegisters.remove(j);
                        }
                    }
                }

                // Update the mode name for the remaining register set.
                for(AtdfRegister reg : possibleRegisters.get(i)) {
                    reg.setMode(possibleModes.get(i));
                }
            }
        }

        for(int i = 0; i < possibleModes.size(); ++i) {
            members_.put(possibleModes.get(i), possibleRegisters.get(i));
        }
    }

    /* Search through the NodeList for special nodes that indicate the modes for the member 
     * registers and return a list of the mode names.
     */
    private List<String> getPossibleMemberModes(NodeList memberNodes) {
        ArrayList<String> modes = new ArrayList<>(5);

        // We'll always have the default mode.
        modes.add("DEFAULT");

        for(int i = 0; i < memberNodes.getLength(); ++i) {
            Node member = memberNodes.item(i);
            String name = member.getNodeName();

            if(name.equals("mode")) {
                String modeName = Utils.getNodeAttribute(member, "name", "");

                if(!modeName.isEmpty()) {
                    modes.add(modeName);
                }
            }
        }

        return modes;
    }

    /* Search through the NodeList for registers and use them to build a series of arrays in which
     * each array contains the registers for a particular mode as given by 'modeList'.
     */
    private List<ArrayList<AtdfRegister>> getPossibleMemberRegistersByMode(List<String> modeList,
                                                                           NodeList memberNodes) {
        ArrayList<ArrayList<AtdfRegister>> registers = new ArrayList<>(modeList.size());

        for(int i = 0; i < modeList.size(); ++i) {
            registers.add(new ArrayList<AtdfRegister>(8));
        }

        for(int i = 0; i < memberNodes.getLength(); ++i) {
            Node member = memberNodes.item(i);
            String name = member.getNodeName();

            if(name.equals("register")  ||  name.equals("register-group")) {
                String modeName = Utils.getNodeAttribute(member, "modes", "DEFAULT");

                int j = 0;
                for(String match : modeList) {
                    if(match.equals(modeName)) {
                        registers.get(j).add(new AtdfRegister(moduleNode_, member));
                        break;
                    }

                    ++j;
                }
            }
        }

        return registers;
    }

    /* Return True if the two lists of registers are equal--that is, the lists are the same size and
     * each corresponding register in the two lists are equal.
     */
    private boolean areRegistersEqual(List<AtdfRegister> list1, List<AtdfRegister> list2) {
        boolean equal = true;
        
        if(list1.size() == list2.size()) {

            for(int i = 0; i < list1.size(); ++i) {
                AtdfRegister reg1 = list1.get(i);
                AtdfRegister reg2 = list2.get(i);

                if(!reg1.equals(reg2)) {
                    equal = false;
                    break;
                }
            }
        } else {
            equal = false;
        }

        return equal;
    }

    /* Return a String containing starting portion of the given two strings that are equivalent.
     * The result will be empty if the strings do not share a common prefix.  This will remove a
     * trailing underscore from the prefix if one is present.
     */
    private String getCommonPrefix(String str1, String str2) {
        int i = 0;
        while(str1.charAt(i) == str2.charAt(i)) {
            ++i;
        }

        if(i > 0  &&  '_' == str1.charAt(i-1)) {
            --i;
        }

        return str1.substring(0, i);
    }
}
