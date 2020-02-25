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
import java.util.List;
import org.w3c.dom.Node;


/**
 * This represents a single register or alias from a register group in an ATDF document.
 * 
 * Register groups can contain either actual registers or placeholders to indicate that the group
 * contains a subgroup.  This class refers to such a placeholder as a "group alias".  This class 
 * handles both because a subgroup is presented similarly to a register that has no fields.
 */
public class AtdfRegister implements Comparable<AtdfRegister> {
    public static final int REG_READ  = 0x01;
    public static final int REG_WRITE = 0x02;
    public static final int REG_RW    = 0x03;

    private final Node moduleNode_;
    private final Node regNode_;
    private final ArrayList<AtdfBitfield> bitfields_ = new ArrayList<>(32);
    private String modeName_ = null;

    /* Create a new AtdfRegister based on the given nodes from an ATDF document.  The 'moduleNode' 
     * is a Node that refers to the "module" XML node that contains the desired register indicated
     * by 'regNode'.  This is handled in the AtdfRegisterGroup class, so use methods in there to get
     * an AtdfRegister object for that peripheral instead of calling this directly.
     */
    public AtdfRegister(Node moduleNode, Node regNode) {
        moduleNode_ = moduleNode;
        regNode_ = regNode;
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

    /* Set the name of the register mode.
     */
    public void setMode(String mode) {
        modeName_ = mode;
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
        long mask = 0;
        
        for(AtdfBitfield bf : getAllBitfields()) {
            mask |= bf.getMask();
        }

        return mask;
    }

    /* Return True if this object actually represents a register group instead of just a single 
     * register.  The two are presented similarly and so there is not much difference between the 
     * two.  A group will of course have no bitfields on its own.
     */
    public boolean isGroupAlias() {
        return regNode_.getNodeName().equals("register-group");
    }

    /* Get all of the modes used by the member bitfields.  Modes allow a register to act differently
     * and even have a different layout based on the operating mode of its owning peripheral.  The
     * default mode name for bitfields is "DEFAULT" and so this list will always contain that.
     */
    public List<String> getBitfieldModes() {
        ArrayList<String> modes = new ArrayList<>(5);
        List<Node> modeNodes = Utils.filterAllChildNodes(regNode_, "mode", null, null);
        
        if(!modeNodes.isEmpty()) {
            for(Node node : modeNodes) {
                modes.add(Utils.getNodeAttribute(node, "name", ""));
            }
        } else {
            // We'll always have a default mode.
            modes.add("DEFAULT");
        }

        return modes;
    }

    /* Get a list of all of the instances for this peripheral.
     */
    public List<AtdfBitfield> getAllBitfields() {
        if(bitfields_.isEmpty()) {
            List<Node> bfList = Utils.filterAllChildNodes(regNode_, "bitfield", null, null);

            for(Node bfNode : bfList) {
                bitfields_.add(new AtdfBitfield(moduleNode_, regNode_, bfNode));
            }
        }

        return bitfields_;
    }

    /* Get a list of all bitfields that are applicable with the given mode.  Returns an empty list
     * if the given mode name is not applicable to any bitfields.
     */
    public List<AtdfBitfield> getBitfieldsByMode(String mode) {
        ArrayList<AtdfBitfield> fields = new ArrayList<>(8);

        for(AtdfBitfield bf : getAllBitfields()) {
            for(String bfModeName : bf.getModes()) {
                if(bfModeName.equals(mode)) {
                    fields.add(bf);
                }
            }
        }
        
        return fields;
    }

    /* Get a single bitfield by name or null if a bitfield by that name canot be found.
     */
    public AtdfBitfield getBitfield(String name) {
        for(AtdfBitfield bf : getAllBitfields()) {
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
    public int compareTo(AtdfRegister other) {
        long myOffset = getBaseOffset();
        long otherOffset = other.getBaseOffset();

        if(myOffset > otherOffset)
            return 1;
        else if(myOffset < otherOffset)
            return -1;
        else
            return 0;
    }
}
