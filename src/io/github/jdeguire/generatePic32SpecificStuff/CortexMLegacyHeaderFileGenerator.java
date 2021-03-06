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
import java.util.Collections;
import java.util.List;
import org.xml.sax.SAXException;

/**
 * A subclass of the HeaderFileBuilder that handles ARM Cortex-M devices.  This generates "legacy"
 * header files that were used when Atmel was still independent.  Microchip has since updated the
 * header files to be compatible with Harmony 3 and those are generally simplified compared to 
 * these ones.
 */
public class CortexMLegacyHeaderFileGenerator extends CortexMBaseHeaderFileGenerator {

    public CortexMLegacyHeaderFileGenerator(String basepath) {
        super(basepath);
    }


    /* Output the license header appropriate for these "legacy" header files, which in this case
     * is the Apache license.
     */
    @Override
    protected void outputLicenseHeader(PrintWriter writer) {
        outputLicenseHeaderApache(writer);
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
        writer_.println("#if !defined DONT_USE_CMSIS_INIT");
        writer_.println("extern uint32_t SystemCoreClock;   /* System (Core) Clock Frequency */");
        writer_.println("void SystemInit(void);");
        writer_.println("void SystemCoreClockUpdate(void);");
        writer_.println("#endif /* DONT_USE_CMSIS_INIT */");
        writer_.println();
    }

    /* Output macros to provide the base address of peripherals along with information on the
     * instances of each peripheral.
     */
    @Override
    protected void outputBaseAddressMacros(List<AtdfPeripheral> peripheralList){
        writeHeaderSectionHeading(writer_, "Peripheral Base Address Macros");

        writeAssemblyStart(writer_);
        outputBaseAddressMacrosForAssembly(peripheralList);
        writer_.println("#else /* !__ASSEMBLER__ */");
        outputBaseAddressMacrosForC(peripheralList);
        writeAssemblyEnd(writer_);
        writer_.println();
    }

    /* Output register macros for all registers belonging to the given group.  This will be
     * recursively called for groups that contain subgroups.  If 'isAssembler' is True, this will
     * output simpler macros that do not include a type.
     */
    @Override
    protected void outputRegisterGroupMacros(PrintWriter writer, AtdfRegisterGroup group, int numGroups,
                                           long groupOffset, AtdfPeripheral peripheral, AtdfInstance instance) {
        for(String mode : group.getNonduplicateModes()) {
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

                            String regNameAsC = getCVariableNameForRegister(register);
                            regNameAsC = removeStartOfString(regNameAsC, peripheral.getName());
                            name += regNameAsC;

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

    /* Output a C struct representing the layout of the given register and a bunch of C macros that 
     * can be used to access the fields within it.  This will also output some descriptive text as
     * given in the ATDF document associated with this device.
     */
    @Override
    protected void outputRegisterDefinition(PrintWriter writer, 
                                            AtdfRegister register,
                                            String groupMode,
                                            String regNamePrefix) throws SAXException {
        List<AtdfBitfield> vecfieldList = Collections.<AtdfBitfield>emptyList();
        String peripheralName = register.getOwningPeripheralName();
        String regNameAsC = getCVariableNameForRegister(register);
        String qualifiedRegName;

        regNameAsC = removeStartOfString(regNameAsC, peripheralName);

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

        String c99type = getC99TypeFromRegisterSize(register);
        int registerWidth = register.getSizeInBytes() * 8;
        List<String> bitfieldModeNames = register.getCoalescedBitfieldModes();

        // Write out starting description text and opening to our union.
        //
        String captionStr = ("/* -------- " + qualifiedRegName + " : (" + peripheralName + " Offset: ");
        captionStr += String.format("0x%02X", register.getBaseOffset());
        captionStr += ") (" + register.getRwAsString() + " " + registerWidth + ") " + register.getCaption();
        captionStr += " -------- */";
        writer.println(captionStr);
        writeNoAssemblyStart(writer);
        writer.println("typedef union {");

        // Write a struct for each bitfield mode with the members being the bitfields themselves.
        //
        for(String bfModeName : bitfieldModeNames) {
            List<AtdfBitfield> bitfieldList = register.getBitfieldsByCoalescedMode(bfModeName);
            if(bitfieldList.isEmpty()) {
                continue;
            }

            int bfNextpos = 0;

            writer.println("  struct {");
            for(AtdfBitfield bitfield : bitfieldList) {
                if(bitfield.getLsb() > bfNextpos) {
                    int fieldWidth = bitfield.getLsb() - bfNextpos;
                    writeBlankBitfieldDeclaration(writer, fieldWidth, c99type);
                }

                writeBitfieldDeclaration(writer, bitfield, c99type);
                bfNextpos = bitfield.getMsb()+1;
            }

            // Fill in gap at end of field if needed.
            if(registerWidth > bfNextpos) {
                int fieldWidth = registerWidth - bfNextpos;
                writeBlankBitfieldDeclaration(writer, fieldWidth, c99type);                
            }

            if(isModeNameDefault(bfModeName)) {
                writer.println("  } bit;");
            } else {
                writer.println("  } " + bfModeName + ";");
            }

            // Registers with a single mode for their bitfields can make use of vecfields to merge
            // adjacent related bitfields.
            if(1 == bitfieldModeNames.size()) {
                vecfieldList = getVecfieldsFromBitfields(bitfieldList);
            }
        }

        // Write out our vecfields if we actually have any.
        if(!vecfieldList.isEmpty()) {
            int vecNextpos = 0;

            writer.println("  struct {");
            for(AtdfBitfield vec : vecfieldList) {
                if(vec.getLsb() > vecNextpos) {
                    int fieldWidth = vec.getLsb() - vecNextpos;
                    writeBlankBitfieldDeclaration(writer, fieldWidth, c99type);
                }

                writeBitfieldDeclaration(writer, vec, c99type);
                vecNextpos = vec.getMsb()+1;
            }

            // Fill in gap at end of field if needed.
            if(registerWidth > vecNextpos) {
                int fieldWidth = registerWidth - vecNextpos;
                writeBlankBitfieldDeclaration(writer, fieldWidth, c99type);                
            }

            writer.println("  } vec;");
        }

        // Finish writing our register union.
        writer.println("  " + c99type + " reg;");
        writer.println("} " + getCTypeNameForRegister(register, groupMode) + ";");
        writeNoAssemblyEnd(writer);
        writer.println();

        // Macros
        //
        writeStringMacro(writer, 
                         qualifiedRegName + "_OFFSET",
                         String.format("(0x%02X)", register.getBaseOffset()),
                         qualifiedRegName + " offset");
        writeStringMacro(writer, 
                         qualifiedRegName + "_RESETVALUE",
                         String.format("_U_(0x%X)", register.getInitValue()),
                         qualifiedRegName + " reset value");
        writeStringMacro(writer,
                         qualifiedRegName + "_MASK",
                         String.format("_U_(0x%X)", register.getMask()),
                         qualifiedRegName + " mask");
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

            List<AtdfBitfield> bitfieldList = register.getBitfieldsByCoalescedMode(bfModeName);
            for(AtdfBitfield bitfield : bitfieldList) {
                writeBitfieldMacros(writer, bitfield, bfModeName, qualifiedRegName, bitfield.getBitWidth() > 1);

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
                long modeMask = register.getMaskByBitfieldMode(bfModeName);

                writeStringMacro(writer,
                                 qualifiedRegName + "_" + bfModeName + "_MASK",
                                 String.format("_U_(0x%X)", modeMask),
                                 qualifiedRegName + " mask for mode " + bfModeName);
                writeStringMacro(writer,
                                 qualifiedRegName + "_" + bfModeName + "_Msk",
                                 String.format("_U_(0x%X)", modeMask),
                                 qualifiedRegName + " mask for mode " + bfModeName);
            }

            writer.println();
        }

        // Finally, output any macros for our vecfields.
        if(!vecfieldList.isEmpty()) {
            for(AtdfBitfield vec : vecfieldList) {
                writeBitfieldMacros(writer, vec, null, qualifiedRegName, true);
            }

            writer.println();
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
        List<String> memberModes = group.getNonduplicateModes();
        List<AtdfRegister> defaultRegs = group.getMembersByMode("DEFAULT");

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

                // Peripherals with a default mode in addition to others should still treat the 
                // default mode registers as such for the purposes of naming.  That is, they should
                // not take the name of the mode into which they've been copied.
                String regMode = reg.getMode();
                if(!isModeNameDefault(regMode)) {
                    regMode = mode;
                }

                String regStr = "  " + getIOMacroFromRegisterAccess(reg) + " ";

                String regTypeName = getCTypeNameForRegister(reg, regMode);
                if(!regNamePrefix.isEmpty()  &&  !regNamePrefix.equals(reg.getOwningPeripheralName() + "_")) {
                    regTypeName = removeStartOfString(regTypeName, reg.getOwningPeripheralName());
                }

                regStr += regTypeName;
                regStr = Utils.padStringWithSpaces(regStr, 36, 4);

                regStr += getCVariableNameForRegister(reg);
                int count = reg.getNumRegisters();
                if(count > 1) {
                    regStr += "[" + count + "]";
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
                    String modeStr = "       " + getCTypeNameForGroup(group, mode);
                    modeStr = Utils.padStringWithSpaces(modeStr, 36, 4);
                    modeStr += mode + ";";
                    writer.println(modeStr);
                }
            }

            writer.println("} " + getCTypeNameForGroup(group, null) + ";");
            writeNoAssemblyEnd(writer);
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

    /* Get a list of register mode names used by these legacy Atmel headers.  Atmel headers grouped
     * registers by coalescing duplicate modes (ie. modes that had the same registers in them).
     */
    @Override
    protected List<String> getRegisterGroupModes(AtdfRegisterGroup group) {
        return group.getNonduplicateModes();
    }


    /* Output base address macros that could be used in assembly code.
     */
    private void outputBaseAddressMacrosForAssembly(List<AtdfPeripheral> peripheralList){
        for(AtdfPeripheral peripheral : peripheralList) {
            if(isArmInternalPeripheral(peripheral)) {
                continue;
            }

            try {
                // For assembly, each instance just has its own macro for its base address.
                for(AtdfInstance instance : peripheral.getAllInstances()) {
                    writeStringMacro(writer_, 
                                     instance.getName(), 
                                     "(0x" + Long.toHexString(instance.getBaseAddress()).toUpperCase() + ")",
                                     instance.getName() + " Base Address");
                }
            } catch(SAXException ex) {
                // Do nothing for now because some peripherals (like crypto) have no useful instance
                // info.
            }
        }
    }

    /* Output base address macros that could be used in C.  This also output macros that mimic an
     * initializer list or array of all of the instances of a each peripheral.
     */
    private void outputBaseAddressMacrosForC(List<AtdfPeripheral> peripheralList){
        for(AtdfPeripheral peripheral : peripheralList) {
            if(isArmInternalPeripheral(peripheral)) {
                continue;
            }

            try {
                List<AtdfInstance> instanceList = peripheral.getAllInstances();
                String peripheralTypename = Utils.makeOnlyFirstLetterUpperCase(peripheral.getName());
                peripheralTypename = String.format("(%-12s*)", peripheralTypename);

                // For C, start with outputting macros for each instance...
                for(AtdfInstance instance : instanceList) {
                    String addrString = "0x" + Long.toHexString(instance.getBaseAddress()).toUpperCase();
                    String valString = "(" + peripheralTypename + addrString + "UL)";

                    writeStringMacro(writer_,
                                     instance.getName(),
                                     valString,
                                     instance.getName() + " Base Address");
                }

                // ...then output a macro for the number of instances...
                writeStringMacro(writer_,
                                 peripheral.getName() + "_INST_NUM",
                                 Integer.toString(instanceList.size()),
                                 "Number of instances for " + peripheral.getName());

                // ...finally, output a macro that puts all instances into an initializer list.
                String instancesStr = "{ ";
                boolean first = true;
                for(AtdfInstance instance : instanceList) {
                    if(!first)
                        instancesStr += ", ";

                    instancesStr += instance.getName();
                    first = false;
                }
                instancesStr += " };";
                writeStringMacro(writer_,
                                 peripheral.getName() + "_INSTS",
                                 instancesStr,
                                 peripheral.getName() + " Instances List");
                writer_.println();
            } catch(SAXException ex) {
                // Do nothing for now because some peripherals (like crypto) have no useful instance
                // info.
            }
        }
    }


    /* Write a bitfield declaration that would be used as part of a C struct.  This writes the 
     * declaration as its own line using PrintWriter::println().
     */
    private void writeBitfieldDeclaration(PrintWriter writer, AtdfBitfield bitfield, String c99type) {
        String fieldName = bitfield.getName();
        String fieldCaption = bitfield.getCaption();
        int fieldWidth = bitfield.getBitWidth();
        int fieldStart = bitfield.getLsb();
        int fieldEnd = bitfield.getMsb();

        String fieldDecl = "    " + c99type + "  " + fieldName + ":" + fieldWidth + ";";
        fieldDecl = Utils.padStringWithSpaces(fieldDecl, 36, 4);

        if(fieldWidth > 1) {
            fieldDecl += String.format("/* bit: %2d..%2d  %s */", fieldStart, fieldEnd, fieldCaption);
        } else {
            fieldDecl += String.format("/* bit:     %2d  %s */", fieldStart, fieldCaption);
        }

        writer.println(fieldDecl);
    }

    /* Write a blank bitfield declaration that would be used as part of a C struct to fill in gaps
     * in the bitfield represented by the struct.  This writes the declaration as its own line 
     * using PrintWriter::println().  The gap will be 'fieldWidth' bits wide.
     */
    private void writeBlankBitfieldDeclaration(PrintWriter writer, int fieldWidth, String c99type) {
        writer.println("    " + c99type + "  :" + fieldWidth + ";");
    }

    /* Write a list of C macros that are used to access the given bitfield with each macro on its
     * own line.  This will always generate a macro to indicate the position of the field within the
     * register, ending in "_Pos", and a mask macro, ending in "_Msk".  The third macro generated
     * depends on the 'useValueMacro' argument. If True, this will generate a function-like macro to
     * set the value of the field.  Otherwise, this will generate another mask macro without the
     * "_Msk" as a compatibility measure with older header files that used those.
     */
    private void writeBitfieldMacros(PrintWriter writer, AtdfBitfield bitfield, String bitfieldMode,
                                        String fullRegisterName, boolean useValueMacro) {
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

        writeStringMacro(writer, posMacroName,
                                 "(" + bitfield.getLsb() + ")",
                                 qualifiedName + ": " + bitfield.getCaption());
        writeStringMacro(writer, maskMacroName,
                                 String.format("_U_(0x%X)", bitfield.getMask()),
                                 "");

        if(useValueMacro) {
            String valueMacroName = baseMacroName + "(value)";

            writeStringMacro(writer, valueMacroName,
                                     String.format("(%s & ((value) << %s))", maskMacroName, posMacroName),
                                     "");
        } else {
            writeStringMacro(writer, baseMacroName,
                                     String.format("_U_(0x%X)", bitfield.getMask()),
                                     "");
        }
    }


    /* Create a name for the group suitable as a C type or struct name in the resulting header file.
     * The type incorporates the owning peripheral name and the mode name if present.  The result
     * will be either "OwnerModeGroup_t", "OwnerGroup_t" if 'mode' is null or empty, or just 
     * "Owner" (no "_t") if the owner and group names are equal.  The latter is because such a
     * type is presumably the name of a top-level peripheral struct/union, so no "_t" is added
     * to keep somewhat compatible with Atmel header files.
     */
    private String getCTypeNameForGroup(AtdfRegisterGroup group, String mode) {
        String name = group.getName();
        String owner = group.getOwningPeripheralName();

        return createCGroupName(name, owner, mode, true);
    }

    /* Create a name for the register suitable as a C variable name in the resulting header file
     * as though it is a member of the mode with the given name.  The mode can also be empty or null
     * in order to not include it in the name.  If this register is a group alias, the name will be 
     * formatted like a group name; otherwise, it will be the register name in all caps.
     */
    private String getCVariableNameForRegister(AtdfRegister reg) {
        String name = reg.getName();

        if(reg.isGroupAlias()) {
            return createCGroupName(name, "", null, false);
        } else {
            return name.toUpperCase();
        }
    }

    /* Create a name for the register suitable as a C type or struct name in the resulting header 
     * file as though it is a member of the mode with the given name.  The mode can also be empty or
     * null in order to not include it in the name.  The type incorporates the owning peripheral 
     * name and the mode name if this register has one.  If this is a group alias, then the name 
     * will be formatted like a group name.  If this is not a group alias and does not have a mode,
     * then the result will look like "OWNER_REGISTER_Type".  If this does have a mode, the result 
     * will be formatted as "OWNER_MODE_REGISTER_Type".
     */
    private String getCTypeNameForRegister(AtdfRegister reg, String mode) {
        String name = reg.getName();
        String owner = reg.getOwningPeripheralName();

        if(reg.isGroupAlias()) {
            return createCGroupName(name, owner, mode, true);
        } else {
            name = removeStartOfString(name, owner);

            owner = owner.toUpperCase();
            mode = mode.toUpperCase();
            name = name.toUpperCase();

            if(isModeNameDefault(mode)) {
                return owner + "_" + name + "_Type";
            } else {
                return owner + "_" + mode + "_" + name + "_Type";                
            }
        }
    }

    /* Create a name for a register group suitable to be used as a C type name or variable name.
     * The result will be either "OwnerModeGroup_t", "OwnerGroup_t" if 'mode' is null or empty, 
     * or just  "Owner" (no "_t") if the owner and group names are equal.  The latter is because
     * such a type is presumably the name of a top-level peripheral struct/union, so no "_t" is
     * added to keep somewhat compatible with Atmel header files.
     */
    private String createCGroupName(String groupName, String ownerName, String modeName,
                                    boolean isType) {
        groupName = removeStartOfString(groupName, ownerName);
        groupName = Utils.underscoresToPascalCase(groupName);
        ownerName = Utils.underscoresToPascalCase(ownerName);

        String typeSuffix = (isType ? "_t" : "");

        if(isModeNameDefault(modeName)) {
            if(groupName.isEmpty()) {
                return ownerName;      // no suffix here for top-level structure
            } else {
                return ownerName + groupName + typeSuffix;
            }
        } else {
            modeName = Utils.underscoresToPascalCase(modeName);

            if(groupName.startsWith(modeName)) {
                return ownerName + groupName + typeSuffix;
            } else {
                return ownerName + modeName + groupName + typeSuffix;
            }
        }
    }
}
