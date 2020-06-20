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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.xml.sax.SAXException;

/**
 * A subclass of the HeaderFileBuilder that handles ARM Cortex-M devices.  This generates new style
 * header files that Microchip generates for use with Harmony 3 and XC32 starting with v2.40.  These
 * header files are generally simplified compared to the older style that Atmel generated in that
 * these do not have structs for each register, only macros.
 */
public class CortexMMicrochipHeaderFileGenerator extends CortexMBaseHeaderFileGenerator {

    public CortexMMicrochipHeaderFileGenerator(String basepath) {
        super(basepath);
    }


    /* Output the license header appropriate for these newer Microchip header files, which in this
     * case is Microchip's proprietary license.
     */
    @Override
    protected void outputLicenseHeader(PrintWriter writer) {
        outputLicenseHeaderMicrochipStandard(writer);
    }

    /* CMSIS is Arm's common interface and function API for all Cortex-based microcontrollers.  This
     * will write out declarations to the main header file related to CMSIS.
     */
    @Override
    protected void outputCmsisDeclarations(String cpuName) {
        writeHeaderSectionHeading(writer_, "CMSIS Includes and declarations");

        // Split a CPU name to get the correct CMSIS header to include.  For example, a 
        // Cortex-M0Plus CPU needs to include the to "core_cm0plus.h" header.
        String headerName = "core_c" + cpuName.split("-", 2)[1].toLowerCase() + ".h";
        writer_.println("#include <" + headerName + ">");

        // The original Atmel files included a separate header that was arbitrarily based on some 
        // device series.  There was hardly anything in there and they all seem to be the same, so 
        // we'll just include the contents here for simplicity.
        writer_.println("#if defined USE_CMSIS_INIT");
        writer_.println("extern uint32_t SystemCoreClock;   /* System (Core) Clock Frequency */");
        writer_.println("void SystemInit(void);");
        writer_.println("void SystemCoreClockUpdate(void);");
        writer_.println("#endif /* USE_CMSIS_INIT */");
        writer_.println();
    }

    /* Output macros to provide the base address of peripheral instances.
     */
    @Override
    protected void outputBaseAddressMacros(List<AtdfPeripheral> peripheralList){
        writeHeaderSectionHeading(writer_, "Peripheral Base Address Macros");

        writeNoAssemblyStart(writer_);
        outputInstanceRegisterAddressMacros(peripheralList);
        writeNoAssemblyEnd(writer_);
        writer_.println();
        outputBareBaseAddressMacros(peripheralList);
        writer_.println();
    }

    /* Output register macros for all registers belonging to the given group.  This will be
     * recursively called for groups that contain subgroups.  If 'isAssembler' is True, this will
     * output simpler macros that do not include a type.
     */
    @Override
    protected void outputRegisterGroupMacros(PrintWriter writer, AtdfRegisterGroup group, int numGroups,
                                               long groupOffset, AtdfPeripheral peripheral, AtdfInstance instance) {
        for(String mode : group.getMemberModes()) {
            for(int g = 0; g < numGroups; ++g) {
                for(AtdfRegister register : group.getMembersByMode(mode)) {
                    long regOffset = register.getBaseOffset();
                    int numRegs = register.getNumRegisters();

                    if(register.isGroupAlias()) {
                        AtdfRegisterGroup subgroup = peripheral.getRegisterGroupByName(register.getName());
                        if(null != subgroup) {
                            outputRegisterGroupMacros(writer, subgroup, numRegs, regOffset, peripheral,
                                                      instance);
                        }
                    } else {
                        long baseAddr = instance.getBaseAddress();
                        int regSize = register.getSizeInBytes();
                        int groupSize = group.getSizeInBytes();

                        for(int i = 0; i < numRegs; ++i) {
                            String name = "REG_" + instance.getName() + "_";
                            if(!isModeNameDefault(mode)) {
                                name += mode + "_";
                            }

                            name += getCVariableNameForRegister(register, false);

                            if(numGroups > 1) {
                                name += g;
                            }
                            if(numRegs > 1)
                            {
                                // If register is a member of a group array and register array, then just
                                // output register 0 to match what Atmel was doing.
                                if(1 == numGroups) {
                                    name += i;
                                } else if(i > 0) {
                                    break;
                                }
                            }

                            long fullAddr = baseAddr + groupOffset + regOffset + (i * regSize) + (g * groupSize);
                            String macroStr = getPeripheralInstanceWrapperMacro(register);
                            String addrStr = String.format("(0x%08X)", fullAddr);

                            writeStringMacro(writer, name, macroStr+addrStr, register.getCaption());
                        }
                    }
                }
            }
        }
    }

    /* Output a bunch of C macros that can be used to access the fields within the given register.  
     * This will also output some descriptive text as given in the ATDF document associated with
     * this device.
     */
    @Override
    protected void outputRegisterDefinition(PrintWriter writer, 
                                            AtdfRegister register,
                                            String groupMode,
                                            String regNamePrefix) throws SAXException {
        String peripheralName = register.getOwningPeripheralName();
        String regNameAsC = getCVariableNameForRegister(register, false);
        String qualifiedRegName;

        if(null == regNamePrefix  ||  regNamePrefix.isEmpty()) {
            if(isModeNameDefault(groupMode))
                qualifiedRegName = peripheralName + "_" + regNameAsC;
            else
                qualifiedRegName = peripheralName + "_" + groupMode + "_" + regNameAsC;            
        } else {
            if(regNamePrefix.equals(peripheralName + "_")) {
                regNameAsC = regNamePrefix + regNameAsC;
            }

            if(isModeNameDefault(groupMode))
                qualifiedRegName = regNameAsC;
            else
                qualifiedRegName = regNameAsC + "_" + groupMode;                        
        }

        int registerWidth = register.getSizeInBytes() * 8;
        List<String> bitfieldModeNames = register.getBitfieldModes();

        // Write out starting description text and opening to our union.
        //
        String captionStr = ("/* -------- " + qualifiedRegName + " : (" + peripheralName + " Offset: ");
        captionStr += String.format("0x%02X", register.getBaseOffset());
        captionStr += ") (" + register.getRwAsString() + " " + registerWidth + ") " + register.getCaption();
        captionStr += " -------- */";
        writer.println(captionStr);

        // Write out macros for the register
        //
        writeStringMacro(writer, 
                         qualifiedRegName + "_REG_OFST",
                         String.format("(0x%02X)", register.getBaseOffset()),
                         qualifiedRegName + " offset");
        writeStringMacro(writer, 
                         qualifiedRegName + "_RESETVALUE",
                         String.format("_U_(0x%X)", register.getInitValue()),
                         qualifiedRegName + " reset value");
        writeStringMacro(writer,
                         qualifiedRegName + "_Msk",
                         String.format("_U_(0x%X)", register.getMask()),
                         qualifiedRegName + " mask");
        writer.println();

        // Write out macros for the bitfields in the register
        //
        for(String bfModeName : bitfieldModeNames) {
            if(bitfieldModeNames.size() > 1) {
                writer.println("/* " + bfModeName + " mode */");
            }

            List<AtdfBitfield> bitfieldList = register.getBitfieldsByMode(bfModeName);
            for(AtdfBitfield bitfield : bitfieldList) {
                writeBitfieldMacros(writer, bitfield, bfModeName, qualifiedRegName);

                // Some bitfields use C macros to indicate what the different values of the bitfield
                // mean.  If this has those, then generate them here.
                List<AtdfValue> fieldValues = bitfield.getFieldValues();
                if(!fieldValues.isEmpty()) {
                    String bfName = bitfield.getName();
                    String valueMacroBasename;

                    if(isModeNameDefault(bfModeName)  ||  bfName.startsWith(bfModeName)) {
                        valueMacroBasename = qualifiedRegName + "_" + bfName + "_";
                    } else {
                        valueMacroBasename = qualifiedRegName + "_" + bfModeName + "_" + bfName + "_";
                    }

                    // Create first set of macros containing option values.
                    for(AtdfValue val : fieldValues) {
                        String valName = val.getName();
                        while(valName.startsWith("_")) {
                            valName = valName.substring(1);
                        }

                        String valueMacroName = valueMacroBasename + valName + "_Val";
                        String valueMacroValue = "_U_(" + val.getValue() + ")";
                        String valueMacroCaption = val.getCaption();
                        writeStringMacro(writer, "  " + valueMacroName, valueMacroValue, valueMacroCaption);
                    }

                    // Now create second set which uses first set.
                    for(AtdfValue val : fieldValues) {
                        String valName = val.getName();
                        while(valName.startsWith("_")) {
                            valName = valName.substring(1);
                        }

                        String optMacroName = valueMacroBasename + valName;
                        String valMacroName = optMacroName + "_Val";
                        String posMacroName = valueMacroBasename + "Pos";
                        writeStringMacro(writer, optMacroName, "(" + valMacroName + " << " + posMacroName + ")", "");
                    }
                }
            }

            // Create a mask macro for each mode if there are multiple modes.
            if(bitfieldModeNames.size() > 1) {
                writeStringMacro(writer,
                                 qualifiedRegName + "_" + bfModeName + "_Msk",
                                 String.format("_U_(0x%X)", register.getMaskByBitfieldMode(bfModeName)),
                                 qualifiedRegName + " mask for mode " + bfModeName);
            }

            writer.println();

            // Registers with a single mode for their bitfields can make use of vecfields to merge
            // adjacent related bitfields, so output macros for those, too.
            if(1 == bitfieldModeNames.size()) {
                List<AtdfBitfield> vecfieldList = getVecfieldsFromBitfields(bitfieldList);

                if(!vecfieldList.isEmpty()) {
                    for(AtdfBitfield vec : vecfieldList) {
                        writeBitfieldMacros(writer, vec, null, qualifiedRegName);
                    }

                    writer.println();
                }
            }
        }
    }

    /* Output a C struct representing the layout of the given register group.  This will output
     * structs for all of this group's modes and an extra union of modes if the group has more than
     * one mode.
     */
    @Override
    protected void outputGroupDefinition(PrintWriter writer,
                                            AtdfRegisterGroup group,
                                            String regNamePrefix) {
        List<String> memberModes = group.getMemberModes();
        List<AtdfRegister> defaultRegs = group.getMembersByMode("DEFAULT");
        ArrayList<String> groupSizeMacros = new ArrayList<>(4);

        for(String mode : memberModes) {
            long regNextOffset = 0;
            long regGapNumber = 1;

            List<AtdfRegister> members = group.getMembersByMode(mode);
            if(members.isEmpty()) {
                continue;
            }

            if(memberModes.size() > 1) {
                // Peripherals with a default mode in addition to others copy the default mode
                // registers into each other mode, so add the default mode registers in that case.
                // We also don't need to process the default mode separately in that case, so we
                // can skip it here.
                if(isModeNameDefault(mode)) {
                    continue;
                }

                if(!defaultRegs.isEmpty()) {
                    members = new ArrayList<>(members);
                    members.addAll(defaultRegs);
                    sortAtdfRegistersByOffset(members);
                }
            }

            writeNoAssemblyStart(writer);
            writer.println("typedef struct {");

            for(AtdfRegister reg : members) {
                long regGap = reg.getBaseOffset() - regNextOffset;

                if(regGap > 0) {
                    String gapStr = "  __IM  uint8_t";
                    gapStr = Utils.padStringWithSpaces(gapStr, 36, 4);
                    gapStr += "Reserved" + regGapNumber + "[" + regGap + "];";

                    writer.println(gapStr);
                    ++regGapNumber;
                    regNextOffset = reg.getBaseOffset();
                }

                String regStr = "  " + getIOMacroFromRegisterAccess(reg) + " ";
                regStr += getCTypeNameForRegister(reg, mode);
                regStr = Utils.padStringWithSpaces(regStr, 36, 4);

                if(regNamePrefix.equals(reg.getOwningPeripheralName() + "_")) {
                    regStr += getCVariableNameForRegister(reg, true);
                } else {
                    regStr += getCVariableNameForRegister(reg, !reg.isGroupAlias()  &&  regNamePrefix.isEmpty());
                }

                int count = reg.getNumRegisters();
                if(count > 1) {
                    regStr += "[" + count + "]";

                    // Group aliases have macros to indicate the size of the alias, so save it for
                    // later since we're in the middle of ouputting our struct right now.
                    if(reg.isGroupAlias()) {
                        String macroName = createCGroupName(reg.getName(), reg.getOwningPeripheralName(), mode, true, false);
                        macroName = macroName.toUpperCase();
                        macroName += "_NUMBER";

                        groupSizeMacros.add(makeStringMacro(macroName, "_U_(" + count + ")", null));
                    }
                }
                regStr += ";";
                regStr = Utils.padStringWithSpaces(regStr, 56, 4);

                regStr += "/* Offset ";
                regStr += String.format("0x%02X", reg.getBaseOffset());
                regStr += ": (" + reg.getRwAsString() + " " + (8*reg.getSizeInBytes())+ ") ";
                regStr += reg.getCaption() + " */";

                writer.println(regStr);
                regNextOffset += count * reg.getSizeInBytes();
            }

            // If the combined size of the registers is not enough to fill the group size, then we should
            // add one last reserved section at the end to act as a pad for alignment purposes.
            if(regNextOffset < group.getSizeInBytes()) {
                long padGap = group.getSizeInBytes() - regNextOffset;
                String padStr = "  __IM  uint8_t";
                padStr = Utils.padStringWithSpaces(padStr, 36, 4);
                padStr += "Reserved" + regGapNumber + "[" + padGap + "];";

                writer.println(padStr);
            }

            // Close out our struct with an alignment attribute if needed.
            int alignment = group.getAlignment();
            String alignmentAttr = "";
            if(alignment > 0) {
                alignmentAttr = " __attribute__((aligned(" + alignment + ")))";
            }

            writer.println("} " + getCTypeNameForGroup(group, mode) + alignmentAttr + ";");
            writeNoAssemblyEnd(writer);
            writer.println();
        }

        // Output a union of modes if this group has multiple non-default modes.  There is always
        // a default mode, even if it is empty, so we need at least 3 modes total.
        if(memberModes.size() > 2) {
            writeNoAssemblyStart(writer);
            writer.println("typedef union {");

            for(String mode : memberModes) {
                if(!isModeNameDefault(mode)  &&  !group.getMembersByMode(mode).isEmpty()) {
                    String modeStr = "    " + getCTypeNameForGroup(group, mode);
                    modeStr = Utils.padStringWithSpaces(modeStr, 36, 4);
                    modeStr += mode + ";";
                    writer.println(modeStr);
                }
            }

            writer.println("} " + getCTypeNameForGroup(group, null) + ";");
            writeNoAssemblyEnd(writer);
            writer.println();
        }

        // Output any group size macros we created.
        if(!groupSizeMacros.isEmpty()) {
            for(String macro : groupSizeMacros) {
                writer.println(macro);
            }

            writer.println();
        }

        // Add an extra section macro if needed.
        String section = group.getMemorySection();
        if(!section.isEmpty()) {
            writeStringMacro(writer, 
                             "SECTION_" + group.getName().toUpperCase(),
                             "__attribute__ ((section(\"." + section + "\")))",
                             null);
            writer.println();
        }
    }

    /* Get a list of register mode names used by these newer Microchip headers.  These headers
     * can just get all of the modes as read from the ATDF document without any extra processing.
     */
    @Override
    protected List<String> getRegisterGroupModes(AtdfRegisterGroup group) {
        return group.getMemberModes();
    }


    /* Output base address macros that could be used in C to indirectly access the registers of
     * a peripheral instance.  These are essentially an address cast to a register struct pointer.
     */
    private void outputInstanceRegisterAddressMacros(List<AtdfPeripheral> peripheralList){
        for(AtdfPeripheral peripheral : peripheralList) {
            if(isArmInternalPeripheral(peripheral)) {
                continue;
            }

            try {
                List<AtdfInstance> instanceList = peripheral.getAllInstances();
                String peripheralTypename = peripheral.getName().toLowerCase() + "_registers_t";

                for(AtdfInstance instance : instanceList) {
                    String addrString = "0x" + Long.toHexString(instance.getBaseAddress()).toUpperCase();
                    String valString = "((" + peripheralTypename + ")" + addrString + "UL)";

                    writeStringMacro(writer_,
                                     instance.getName() + "_REGS",
                                     valString,
                                     instance.getName() + " Instance Register Address");
                }
            } catch(SAXException ex) {
                // Do nothing for now because some peripherals (like crypto) have no useful instance
                // info.
            }
        }
    }

    /* Output bare base address macros that just contain the addresses of the peripheral instances.
     */
    private void outputBareBaseAddressMacros(List<AtdfPeripheral> peripheralList){
        for(AtdfPeripheral peripheral : peripheralList) {
            if(isArmInternalPeripheral(peripheral)) {
                continue;
            }

            try {
                for(AtdfInstance instance : peripheral.getAllInstances()) {
                    String addrStr = "0x" + Long.toHexString(instance.getBaseAddress()).toUpperCase();
                    writeStringMacro(writer_, 
                                     instance.getName() + "_BASE_ADDRESS",
                                     "_UL_(" + addrStr + ")",
                                     instance.getName() + " Base Address");
                }
            } catch(SAXException ex) {
                // Do nothing for now because some peripherals (like crypto) have no useful instance
                // info.
            }
        }
    }



    /* Write a set of C macros that are used to access the given bitfield with each macro on its own
     * line.  There's a macro to get the bitfield position within the register, another to get its
     * mask, and the third is a function-like macro to set the value of the field.
     */
    private void writeBitfieldMacros(PrintWriter writer, AtdfBitfield bitfield, String bitfieldMode,
                                        String fullRegisterName) {
        String bitfieldName = bitfield.getName();
        String qualifiedName = fullRegisterName + "<" + bitfieldName + ">";
        String baseMacroName;

        if(isModeNameDefault(bitfieldMode)  ||  bitfieldName.startsWith(bitfieldMode)) {
            baseMacroName = fullRegisterName + "_" + bitfield.getName();
        } else {
            baseMacroName = fullRegisterName + "_" + bitfieldMode + "_" + bitfieldName;
        }

        String posMacroName = baseMacroName + "_Pos";
        String maskMacroName = baseMacroName + "_Msk";
        String valueMacroName = baseMacroName + "(value)";

        writeStringMacro(writer, posMacroName,
                                 "(" + bitfield.getLsb() + ")",
                                 qualifiedName + ": " + bitfield.getCaption());
        writeStringMacro(writer, maskMacroName,
                                 String.format("_U_(0x%X)", bitfield.getMask()),
                                 "");
        writeStringMacro(writer, valueMacroName,
                                 String.format("(%s & ((value) << %s))", maskMacroName, posMacroName),
                                 "");
    }



    /* Create a name for the group suitable as a C type or struct name in the resulting header file.
     * The type incorporates the owning peripheral name and the mode name if present.  The result
     * will be either "owner_mode_group_registers_t", "owner_group_registers_t" if 'mode' is null 
     * or empty, or just  "owner_registers_t" if the owner and group names are equal.  For variables
     * ('isType' is False), the result is just the group name in upper-case.  The owner will be 
     * removed from the group name if it is present for variables.
     */
    private String getCTypeNameForGroup(AtdfRegisterGroup group, String mode) {
        String name = group.getName();
        String owner = group.getOwningPeripheralName();

        return createCGroupName(name, owner, mode, true, true);
    }

    /* Create a type name suitable for a register in a struct.  If the register is a group alias,
     * the name will be formatted just like 'getCTypeNameForGroup()'.  Otherwise, this will get the 
     * C99 type to match the size of the register.
    */
    private String getCTypeNameForRegister(AtdfRegister reg, String mode) {
        if(reg.isGroupAlias()) {
            return createCGroupName(reg.getName(), reg.getOwningPeripheralName(), mode, true, true);
        } else {
            return getC99TypeFromRegisterSize(reg);
        }
    }

    /* Create a name for the register suitable as a C variable name in the resulting header file
     * as though it is a member of the mode with the given name.  If this register is a group alias,
     * the name will be formatted like a group name; otherwise, it will be the register name in all 
     * caps.  If 'includeOwner' is True, the name will start with the name of the owning peripheral;
     * otherwise, this will strip off the owning peripheral name if it is present.
     */
    private String getCVariableNameForRegister(AtdfRegister reg, boolean includeOwner) {
        String name = reg.getName();
        String owner = reg.getOwningPeripheralName();

        name = name.toUpperCase();

        if(includeOwner) {
            if(!name.startsWith(owner)) {
                name = owner + "_" + name;
            }
        } else {
            name = removeStartOfString(name, owner);
        }

        return name;
    }

    /* Create a name for a register group suitable to be used as a C type name or variable name.
     * For types ('isType' is True), the result will be either "owner_mode_group_registers_t", 
     * "owner_group_registers_t" if 'mode' is null or empty, or just  "owner_registers_t" if the 
     * owner and group names are equal.  For variables ('isType' is False), the result is just the
     * group name in upper-case.  The owner will be removed from the group name if it is present for
     * variables.  The "_register_t" suffix for types is optional and added if 'useSuffix' is True.
     */
    private String createCGroupName(String groupName, String ownerName, String modeName,
                                    boolean isType, boolean useSuffix) {
        if(isType) {
            String typeSuffix = (useSuffix ? "_registers_t" : "");

            groupName = removeStartOfString(groupName, ownerName).toLowerCase();
            ownerName = ownerName.toLowerCase();

            if(isModeNameDefault(modeName)) {
                if(!groupName.isEmpty()) {
                    ownerName += "_";
                }

                return ownerName + groupName + typeSuffix;
            } else {
                modeName = modeName.toLowerCase();

                if(groupName.isEmpty()) {
                    return ownerName + "_" + modeName + typeSuffix;  
                } else if(groupName.startsWith(modeName)) {
                    return ownerName + "_" + groupName + typeSuffix;
                } else {
                    return ownerName + "_" + groupName + "_" + modeName + typeSuffix;
                }
            }
        } else {
            return removeStartOfString(groupName, ownerName).toUpperCase();
        }
    }
}
