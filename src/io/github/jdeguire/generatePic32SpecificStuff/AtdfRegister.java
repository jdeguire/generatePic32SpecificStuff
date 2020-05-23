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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.w3c.dom.Node;


/**
 * This represents a single register or alias from a register group in an ATDF document.
 * 
 * Register groups can contain either actual registers or placeholders to indicate that the group
 * contains a subgroup.  This class refers to such a placeholder as a "group alias".  This class 
 * handles both because a subgroup is presented similarly to a register that has no fields.
 */
public class AtdfRegister {
    public static final int REG_READ  = 0x01;
    public static final int REG_WRITE = 0x02;
    public static final int REG_RW    = 0x03;

    private final Node moduleNode_;
    private final Node regNode_;
    private final LinkedHashMap<String, ArrayList<AtdfBitfield>> bitfieldMap_ = new LinkedHashMap<>();
    private final LinkedHashMap<String, ArrayList<AtdfBitfield>> coalescedMap_ = new LinkedHashMap<>();
    private String modeName_ = null;
    private final boolean isGroupAlias_;


    /* Create a new AtdfRegister based on the given nodes from an ATDF document.  The 'moduleNode' 
     * is a Node that refers to the "module" XML node that contains the desired register indicated
     * by 'regNode'.  This is handled in the AtdfRegisterGroup class, so use methods in there to get
     * an AtdfRegister object for that peripheral instead of calling this directly.
     */
    public AtdfRegister(Node moduleNode, Node regNode) {
        moduleNode_ = moduleNode;
        regNode_ = regNode;

        isGroupAlias_ = regNode_.getNodeName().equals("register-group");
    }


    /* Get the register name as it appears in the ATDF document.
     */
     public String getName() {
         return Utils.getNodeAttribute(regNode_, "name", "");
     }

    /* Get the name of the peripheral that owns this register.
     */
    public String getOwningPeripheralName() {
        return Utils.getNodeAttribute(moduleNode_, "name", "");        
    }

    /* Return the name of the mode under which this register operates or an empty string if no mode
     * is available (in which case this register would operate under all peripheral modes).  Some
     * peripherals can have multiple operating modes, such as the SERCOM peripheral being able to 
     * act like a SPI, I2C, or UART peripheral.
     */
    public String getMode() {
        if(null == modeName_) {
            modeName_ = Utils.getNodeAttribute(regNode_, "modes", "");
        }

        return modeName_;
    }

    /* Get descriptive text for this register.
     */
    public String getCaption() {
        return Utils.getNodeAttribute(regNode_, "caption", "");
    }

    /* Get number of bytes from which this register is offset from the base peripheral instance
     * address or register group address.
     */
    public long getBaseOffset() {
        return Utils.getNodeAttributeAsLong(regNode_, "offset", 0);
    }

    /* Return a string indicating the read/write access of this register.  This will return either
     * "R", "W", or "RW".
     */
    public String getRwAsString() {
        return Utils.getNodeAttribute(regNode_, "rw", "R");
    }
    
    /* Return a value indicating the read/write access of this register.  Use the flags at the top
     * of this file to check the access.
     */
    public int getRwAsInt() {
        int rw = 0;
        char[] rwChars = getRwAsString().toCharArray();

        for(char c : rwChars) {
           if('r' == c  ||  'R' == c) {
               rw |= REG_READ;
           } else if('w' == c  ||  'W' == c) {
               rw |= REG_WRITE;
           }
       }

        return rw;
    }

    /* Get the size of the register in bytes.  If this is a group alias, then the size can be larger
     * than the total size of the registers in the group to indicate that it requires extra pad 
     * bytes at the end.  If no extra padding is required for an alias, then this will return 0.
     */
    public int getSizeInBytes() {
        int defaultSize = 1;

        if(isGroupAlias())
            defaultSize = 0;

        return Utils.getNodeAttributeAsInt(regNode_, "size", defaultSize);
    }

    /* Get the number of registers that match this definition.  If this is greater than 1, then this
     * register represents an array of registers of the same format.  If this is a group alias, then
     * this represents an array of groups of the same format.
     */
    public int getNumRegisters() {
        return Utils.getNodeAttributeAsInt(regNode_, "count", 1);
    }

    /* Get the initial value of the register on device reset or power-up.
     */
    public long getInitValue() {
        return Utils.getNodeAttributeAsInt(regNode_, "initval", 0);
    }

    /* Return a mask in which bits set to 1 are actually used by the register.
     */
    public long getMask() {
        if(isGroupAlias()) {
            return 0;
        } else {
            List<AtdfBitfield> allBitfields = getAllBitfields();

            if(allBitfields.isEmpty()) {
                // This seems to be a special case for registers that have no bitfields to imply
                // that the entire register is one field.
                return (1L << (8 * getSizeInBytes())) - 1L;
            } else {
                long mask = 0;

                for(AtdfBitfield bf : getAllBitfields()) {
                    mask |= bf.getMask();
                }

                return mask;
            }
        }
    }

    /* Return a mask in which bits set to 1 are used by the register when in the given bitfield mode.
     * For example, some bits may be used by a SERCOM peripheral while in SPI mode, but not while in
     * UART mode.
     */
    public long getMaskByBitfieldMode(String mode) {
        List<AtdfBitfield> bitfields = getBitfieldsByMode(mode);
        long mask = 0;

        for(AtdfBitfield bf : bitfields) {
            mask |= bf.getMask();
        }

        return mask;
    }

    /* Return True if this object actually represents a register group instead of just a single 
     * register.  The two are presented similarly and so there is not much difference between the 
     * two.  A group will of course have no bitfields on its own.
     */
    public boolean isGroupAlias() {
        return isGroupAlias_;
    }

    /* Get all of the modes used by the member bitfields.  Modes allow a register to act differently
     * and even have a different layout based on the operating mode of its owning peripheral.  The
     * default mode name for bitfields is "DEFAULT" and so any bitfield that does not belong to
     * any particular mode will use that one.
     */
    public List<String> getBitfieldModes() {
        if(bitfieldMap_.isEmpty()) {
            populateBitfieldMap();
        }

        return new ArrayList<>(bitfieldMap_.keySet());
    }

    /* Get all of the modes used by the member bitfields after bitfields common to all non-default
     * modes have been coalesced into the default mode.
     */
    public List<String> getCoalescedBitfieldModes() {
        if(coalescedMap_.isEmpty()) {
            populateCoalescedMap();
        }

        return new ArrayList<>(coalescedMap_.keySet());
    }

    /* Get a list of all of the bitfields for this peripheral regardless of mode.  Note that multiple
     * bitfields with the same name and mask, and thus are therefore equivalent, can be present in
     * this list because an AtdfBitfield represents a single entry in an ATDF document and whether
     * there are duplicates depends on how the document was created.
     */
    public List<AtdfBitfield> getAllBitfields() {
        if(bitfieldMap_.isEmpty()) {
            populateBitfieldMap();
        }
        
        ArrayList<AtdfBitfield> allBitfields = new ArrayList<>();
        for(ArrayList<AtdfBitfield> fields : bitfieldMap_.values()) {
            allBitfields.addAll(fields);
        }

        return allBitfields;
   }

    /* Get a list of all bitfields after all of the bitfields common to the non-default modes have
     * been coalesced into the default mode.  Note that these bitfields will still provide their
     * original modes when using AtdfBitfield::getModes().
     * 
     * If you want to categorize bitfields by their coalesced modes, use getCoalescedBitfieldModes()
     * to get a list of the modes and then call getCoalescedBitfieldsByMode() to get the bitfields 
     * by their coalesced modes.
     */
    public List<AtdfBitfield> getAllCoalescedBitfields() {
        if(coalescedMap_.isEmpty()) {
            populateCoalescedMap();
        }

        ArrayList<AtdfBitfield> allBitfields = new ArrayList<>();
        for(ArrayList<AtdfBitfield> fields : coalescedMap_.values()) {
            allBitfields.addAll(fields);
        }

        return allBitfields;        
    }

    /* Get a list of all bitfields that are applicable with the given mode.  Returns an empty list
     * if the given mode name is not applicable to any bitfields.  Use "DEFAULT" to get the default
     * mode, which contains any bitfields that do not belong to a particular mode.
     */
    public List<AtdfBitfield> getBitfieldsByMode(String mode) {
        if(bitfieldMap_.isEmpty()) {
            populateBitfieldMap();
        }

        return bitfieldMap_.getOrDefault(mode, new ArrayList<AtdfBitfield>());
    }

    /* Get a list of all bitfields that are applicable with the given coalesced mode.  That is, this
     * will return a list of bitfields in which any bitfields common to all non-default modes are
     * moved to the default mode.  Returns an empty list if the given mode name is not applicable to
     * any bitfields.  Use "DEFAULT" to get the default mode, which contains any bitfields that do 
     * not belong to a particular mode.
     */
    public List<AtdfBitfield> getBitfieldsByCoalescedMode(String mode) {
        if(coalescedMap_.isEmpty()) {
            populateCoalescedMap();
        }

        return coalescedMap_.getOrDefault(mode, new ArrayList<AtdfBitfield>());
    }

    /* Get a single bitfield by name or null if a bitfield by that name canot be found.  It is
     * possible for multiple bitfields to have the name, particularly if they're in different modes.
     * This will return the first one found.  Generally, fields with the same name also have the 
     * same mask and so they're usually equivalent, but there is no guarantee of that.
     */
    public AtdfBitfield getBitfield(String name) {
        List<AtdfBitfield> allBitfields = getAllBitfields();

        for(AtdfBitfield bf : allBitfields) {
            if(name.equals(bf.getName()))
                return bf;
        }

        return null;
    }

    /* Get a single bitfield by name that belongs to the given mode.  Use getBitfieldModes() to see
     * what modes are available to select.  Returns null if the given mode or a bitfield by the
     * given name does not exist.
     */
    public AtdfBitfield getBitfieldByMode(String name, String mode) {
        List<AtdfBitfield> modeBitfields = getBitfieldsByMode(mode);

        for(AtdfBitfield bf : modeBitfields) {
            if(name.equals(bf.getName()))
                return bf;
        }

        return null;
    }

    /* Get a single bitfield by name that belongs to the given coalesced mode.  Use 
     * getCoalescedBitfieldModes() to see what modes are available to select.  Returns null if the 
     * given mode or a bitfield by the given name does not exist.
     */
    public AtdfBitfield getBitfieldByCoalescedMode(String name, String mode) {
        List<AtdfBitfield> modeBitfields = getBitfieldsByCoalescedMode(mode);

        for(AtdfBitfield bf : modeBitfields) {
            if(name.equals(bf.getName()))
                return bf;
        }

        return null;
    }

    /* Return True if this register is equal to the other--that is, they have the same name and
     * bitfields.
     */
    public boolean equals(AtdfRegister other) {
        boolean equal = false;

        if(getName().equals(other.getName())) {
            List<AtdfBitfield> ourBitfields = getAllBitfields();
            List<AtdfBitfield> theirBitfields = other.getAllBitfields();

            if(ourBitfields.size() == theirBitfields.size()) {
                equal = true;

                for(int i = 0; i < ourBitfields.size(); ++i) {
                    if(!ourBitfields.get(i).equals(theirBitfields.get(i))) {
                        equal = false;
                        break;
                    }
                }
            }
        }

        return equal;
    }

    @Override
    public boolean equals(Object other) {
        if(other instanceof AtdfRegister) {
            return equals((AtdfRegister)other);
        } else {
            return false;
        }
    }

    /* Netbeans suggested I add this and generated it for me when I made equals(Object). */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + Objects.hashCode(this.moduleNode_);
        hash = 61 * hash + Objects.hashCode(this.regNode_);
        return hash;
    }


    /* Populate the object's map of AtdfBitfields by simply add bitfields as they are presented in 
     * the ATDF document.
     */
    private void populateBitfieldMap() {
        if(isGroupAlias()) {
            // Group aliases don't have bitfields of their own since they represent a group of
            // other registers.
            return;
        }

        bitfieldMap_.clear();
        List<AtdfBitfield> allBitfields = getBitfieldsFromDoc();

        for(AtdfBitfield bf : allBitfields) {
            List<String> bfModeList = bf.getModes();

            for(String bfMode : bfModeList) {
                addBitfieldToMap(bitfieldMap_, bf, bfMode);
            }
        }
    }

    /* Populate the object's coalesced map of AtdfBitfields, which consists of bitfields in which
     * ones common to all non-default modes are moved to the default mode.
     */
    private void populateCoalescedMap() {
        if(isGroupAlias()) {
            // Group aliases don't have bitfields of their own since they represent a group of
            // other registers.
            return;
        }

        coalescedMap_.clear();
        List<AtdfBitfield> allBitfields = getBitfieldsFromDoc();
        List<String> allModes = getBitfieldModesFromDoc();

        for(AtdfBitfield bf : allBitfields) {
            List<String> bfModeList = findModesForBitfield(bf, allBitfields);
            boolean isCommonBitfield = true;

            // This is a common bitfield if it is a member of all of the possible modes for this field.
            for(String mode : allModes) {
                if(!bfModeList.contains(mode)) {
                    isCommonBitfield = false;
                    break;
                }
            }

            if(isCommonBitfield) {
                addBitfieldToMap(coalescedMap_, bf, "DEFAULT");
            } else {
                for(String bfMode : bfModeList) {
                    addBitfieldToMap(coalescedMap_, bf, bfMode);
                }
            }
        }
    }

    /* Get all of the bitfields that are a part of this register by reading them from XML nodes in
     * the ATDF document that describe them.
     */
    private List<AtdfBitfield> getBitfieldsFromDoc() {
        ArrayList<AtdfBitfield> allBitfields = new ArrayList<>();

        // Read all of the bitfield nodes from the ATDF document.
        List<Node> bfList = Utils.filterAllChildNodes(regNode_, "bitfield", null, null);
        for(Node bfNode : bfList) {
            allBitfields.add(new AtdfBitfield(moduleNode_, regNode_, bfNode));
        }

        return allBitfields;
    }

    /* Get all of the modes the bitfields can populate by reading XML nodes in the ATDF document
     * that call them out.  Bitfield modes let a register have different bitfields based on how a
     * peripheral is configured.  For example, the TC (timer/counter) peripheral has bitfield modes
     * for outputting a waveform vs. capturing an incoming one.
     */
    private List<String> getBitfieldModesFromDoc() {
        ArrayList<String> allModes = new ArrayList<>();

        // Read all of the bitfield nodes from the ATDF document.
        List<Node> bfList = Utils.filterAllChildNodes(regNode_, "mode", null, null);
        for(Node bfNode : bfList) {
            String modeName = Utils.getNodeAttribute(bfNode, "name", "DEFAULT");

            if(!allModes.contains(modeName)) {
                allModes.add(modeName);
            }
        }

        return allModes;
    }

    /* Given a single AtdfBitfield and a list of them, search through the list for all equivalent
     * bitfields and use them all to build up a list of the modes that the given bitfield can have.
     *
     * The ATDF doc can either have a single bitfield that lists all of its modes or have duplicate
     * bitfields with each one having a different mode.  This function exists because of the latter.
     */
    private List<String> findModesForBitfield(AtdfBitfield bf, List<AtdfBitfield> bfList) {
        ArrayList<String> possibleModes = new ArrayList<>();

        for(AtdfBitfield other : bfList) {
            if(bf.equals(other)) {
                // This will always retutrn something, so no need to check for an empty list.
                List<String> otherModeList = other.getModes();

                for(String otherMode : otherModeList) {
                    if(!possibleModes.contains(otherMode)) {
                        possibleModes.add(otherMode);
                    }
                }
            }
        }
        
        return possibleModes;
    }

    /* Just a simple convenience method to add the given bitfield to our map with the given mode.
     * This will create a new array for the given mode if needed and will not add the bitfield if
     * an equivalent one is already in the list for the given mode.
     */
    private void addBitfieldToMap(LinkedHashMap<String, ArrayList<AtdfBitfield>> bfMap,
                                  AtdfBitfield bf,
                                  String mode) {
        if(!bfMap.containsKey(mode)) {
            bfMap.put(mode, new ArrayList<AtdfBitfield>());
        }

        if(!bfMap.get(mode).contains(bf)) {
            bfMap.get(mode).add(bf);
        }
    }
}
