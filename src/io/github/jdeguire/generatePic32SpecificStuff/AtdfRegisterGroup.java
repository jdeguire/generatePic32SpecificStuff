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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private final LinkedHashMap<String, ArrayList<AtdfRegister>> members_ = new LinkedHashMap<>(8);
    private final LinkedHashMap<String, String> nonduplicateModes_ = new LinkedHashMap<>(8);
    
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

    /* Some devices, such as the SAME70, name registers in a peripheral with a commong prefix such
     * as "MCAN_REG1" and "MCAN_REG2".  This prefix is usually the name of the peripheral, but not
     * always.  This will look for such a prefix and return it or an empty string if no such prefix
     * appears to be used (such as on the SAME54).  The register names must have an underscore that
     * separates a potential prefix from its name in order to count.
    */
    public String getMemberNamePrefix() {
        String prefix = "";

        List<AtdfRegister> members = getAllMembers();        

        AtdfRegister first = members.get(0);
        int index = first.getName().indexOf('_');

        // >0 so that we don't end up with something like "_REG" returning just "_".
        if(index > 0) {
            prefix = first.getName().substring(0, index+1);     // +1 to include '_'

            for(AtdfRegister reg : members) {
                if(!reg.getName().startsWith(prefix)) {
                    prefix = "";
                    break;
                }
            }
        }

        return prefix;
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

    /* Like getMemberModes(), but returns a list of mode names that contain unique members.  These 
     * will usually be similar to normal mode names, but may have part of the end of the name removed.
     * For example, if there were two duplicate modes "MODE1" and "MODE2", this would return just 
     * "MODE" as one of the modes.
     */
    public List<String> getNonduplicateModes() {
        if(nonduplicateModes_.isEmpty()) {
            populateNonduplicateMap();
        }

        return new ArrayList<>(nonduplicateModes_.keySet());
    }

    /* Return the registers that are a part of the given mode or an empty List if the given mode 
     * name does not exist.  This will work for mode names retrieved using either getMemberModes()
     * or getNonduplicateModes(), with the former taking precendence.
     */
    public List<AtdfRegister> getMembersByMode(String modeName) {
        if(members_.isEmpty()) {
            populateMemberMap();
        }

        if(members_.containsKey(modeName)) {
            return members_.get(modeName);
        } else {
            if(nonduplicateModes_.isEmpty()) {
                populateNonduplicateMap();
            }

            if(nonduplicateModes_.containsKey(modeName)) {
                return members_.get(nonduplicateModes_.get(modeName));
            } else {
                return Collections.<AtdfRegister>emptyList();
            }
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

        // First, we need to find all of the mode names and initialize the map with them.
        // There is always a "DEFAULT" mode, which is the mode used when a register does not give
        // a mode.
        members_.put("DEFAULT", new ArrayList<AtdfRegister>(16));

        for(int i = 0; i < children.getLength(); ++i) {
            Node member = children.item(i);
            String name = member.getNodeName();

            if(name.equals("mode")) {
                String modeName = Utils.getNodeAttribute(member, "name", "");

                if(!modeName.isEmpty()) {
                    members_.put(modeName, new ArrayList<AtdfRegister>(16));
                }
            }
        }

        // Now, we can sort the registers into their proper modes.
        for(int i = 0; i < children.getLength(); ++i) {
            Node member = children.item(i);
            String name = member.getNodeName();

            if(name.equals("register")  ||  name.equals("register-group")) {
                String modeName = Utils.getNodeAttribute(member, "modes", "DEFAULT");

                if(members_.containsKey(modeName)) {
                    members_.get(modeName).add(new AtdfRegister(moduleNode_, member));
                }
            }
        }
    }

    /* Populate the mapping from nondupicate mode names to the regular mode name, which would then
     * be used as the key to look up the registers by that mode.  The original Atmel headers would
     * coalesce modes that were duplicates--ie. had the same registers--so this will let us do the
     * same.  Note that since XC32 v2.40 and Harmony 3, Microchip is using a new header file format
     * that does not coalesce modes.
    */
    private void populateNonduplicateMap() {
        if(members_.isEmpty()) {
            populateMemberMap();
        }

        List<String> modeList = getMemberModes();
        boolean[] alreadyTaken = new boolean[modeList.size()];

        for(int i = 0; i < modeList.size(); ++i) {
            if(alreadyTaken[i]) {
                continue;
            }

            String iModeName = modeList.get(i);
            List<AtdfRegister> iRegisters = members_.get(iModeName);
            String commonPrefix = iModeName;
            boolean foundDuplicate = false;

            for(int j = i+1; j < modeList.size(); ++j) {
                if(alreadyTaken[j]) {
                    continue;
                }

                String jModeName = modeList.get(j);
                List<AtdfRegister> jRegisters = members_.get(jModeName);

                if(areRegistersEqual(iRegisters, jRegisters)) {
                    // These modes are duplicates because their registers are equal, so mark this 
                    // as already found and add it to the map if they share a part of their name.
                    String possiblePrefix = getCommonPrefix(commonPrefix, jModeName);

                    if(!possiblePrefix.isEmpty()) {
                        nonduplicateModes_.put(possiblePrefix, iModeName);
                        commonPrefix = possiblePrefix;
                        alreadyTaken[j] = true;
                        foundDuplicate = true;
                    }
                }
            }

            if(!foundDuplicate) {
                // No duplicates were found for this mode, so add it to the list now.
                nonduplicateModes_.put(iModeName, iModeName);
            }
        }
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
        int len = (str1.length() > str2.length() ? str2.length() : str1.length());

        while(str1.charAt(i) == str2.charAt(i)  &&  i < len) {
            ++i;
        }

        if(i > 0  &&  '_' == str1.charAt(i-1)) {
            --i;
        }

        return str1.substring(0, i);
    }
}
