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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.xml.sax.SAXException;

/**
 * A subclass of the HeaderFileBuilder that handles ARM Cortex-M devices.
 */
public class CortexMHeaderFileBuilder extends HeaderFileBuilder {

    /* This is here for convenience when we create custom fields that are "vectors" of adjacent 
     * fields or when we create gaps.
     */
    private class CustomBitfield extends AtdfBitfield {
        public String name_;
        public String owner_;
        public String caption_;
        public long mask_;

        CustomBitfield(String name, String owner, String caption, long mask) {
            super(null, null, null);
            name_ = name;
            owner_ = owner;
            caption_ = caption;
            mask_ = mask;
        }

        CustomBitfield() {
            this("", "", "", 0);
        }

        // Copy constructor
        CustomBitfield(AtdfBitfield other) {
            this(other.getName(), other.getOwningRegisterName(), other.getCaption(), other.getMask());
        }

        @Override
        public String getName() { return name_; }
        
        @Override
        public String getOwningRegisterName() { return owner_; }

        @Override
        public List<String> getModes() { 
            List<String> modes = new ArrayList<>(1);
            modes.add("DEFAULT");
            return modes; 
        }

        @Override
        public String getCaption() { return caption_; }
        
        @Override
        public long getMask() { return mask_; }
        
        @Override
        public List<AtdfValue> getFieldValues() { return Collections.<AtdfValue>emptyList(); }

        public void updateMask(long update) { mask_ |= update; }
    }

    private final HashSet<String> peripheralFiles_ = new HashSet<>(20);


    public CortexMHeaderFileBuilder(String basepath) {
        super(basepath);
    }

    @Override
    public void generate(TargetDevice target) 
                                    throws java.io.FileNotFoundException, SAXException {
        String basename = target.getDeviceName();
        AtdfDoc atdfDoc;
        try {
            atdfDoc = new AtdfDoc(basename);
        } catch(SAXException ex) {
            throw ex;
        } catch(Exception ex) {
            throw new java.io.FileNotFoundException(ex.getMessage());
        }

        createNewHeaderFile(target);

        List<AtdfPeripheral> peripherals = atdfDoc.getAllPeripherals();

        outputLicenseHeader(writer_, true);
        outputPeripheralDefinitionHeaders(peripherals);
        outputPeripheralInstancesHeader(target, peripherals);

        closeHeaderFile();
    }


    /* Each peripheral has a header file filled with structs and macros describing its member SFRs
     * and layout.  This will output said header files for this device, skipping over ones that have
     * been already output from previous calls, and write the include directives to the main device 
     * file needed to include these files.
     */
    private void outputPeripheralDefinitionHeaders(List<AtdfPeripheral> peripheralList) 
                                    throws java.io.FileNotFoundException, SAXException {
        for(AtdfPeripheral peripheral : peripheralList) {
            String peripheralName = peripheral.getName();
            String peripheralId = peripheral.getModuleId();
            String peripheralMacro = (peripheralName + "_" + peripheralId).toUpperCase();
            String peripheralFilename = "component/" + peripheralMacro.toLowerCase() + ".h";

            if(isArmInternalPeripheral(peripheral)) {
                continue;
            }

            // Did we already generate a file for this peripheral?
            if(!peripheralFiles_.contains(peripheralFilename)) {
                // Nope, so time to create one.
                String filepath = basepath_ + "/" + peripheralFilename;

                try(PrintWriter peripheralWriter = Utils.createUnixPrintWriter(filepath)) {
                    // Output top-of-file stuff like license and include guards.
                    outputLicenseHeader(peripheralWriter, true);
                    peripheralWriter.println();
                    peripheralWriter.println("#ifndef _" + peripheralMacro + "_COMPONENT_");
                    peripheralWriter.println("#define _" + peripheralMacro + "_COMPONENT_");
                    peripheralWriter.println();
                    writeStringMacro(peripheralWriter, peripheralMacro, "", "");
                    writePeripheralVersionMacro(peripheralWriter, peripheral);
                    peripheralWriter.println();

                    // Output definitions for each register in all of the groups.
                    for(AtdfRegisterGroup registerGroup : peripheral.getAllRegisterGroups()) {
                        for(String mode : registerGroup.getMemberModes()) {
                            for(AtdfRegister register : registerGroup.getMembersByMode(mode)) {
                                if(!register.isGroupAlias())
                                    outputRegisterDefinition(peripheralWriter, register, mode);
                            }
                        }
                    }

                    // Now output definitions for the groups themselves
                    for(AtdfRegisterGroup registerGroup : peripheral.getAllRegisterGroups()) {
                        outputGroupDefinition(peripheralWriter, registerGroup);
                    }

                    // End-of-file stuff
                    peripheralWriter.println();
                    peripheralWriter.println("#endif /* _" + peripheralMacro + "_COMPONENT_ */");
                }

                peripheralFiles_.add(peripheralFilename);
            }

            // Include our new peripheral definition header in our main header file.
            writer_.println("#include \"" + peripheralFilename + "\"");
        }

        writer_.println();
    }

    /* Output a C struct representing the layout of the given register and a bunch of C macros that 
     * can be used to access the fields within it.  This will also output some descriptive text as
     * given in the ATDF document associated with this device.
     */
    private void outputRegisterDefinition(PrintWriter writer, AtdfRegister register, String groupMode)
                                            throws SAXException {
        ArrayList<AtdfBitfield> bitfieldList = new ArrayList<>(32);
        ArrayList<AtdfBitfield> vecfieldList = new ArrayList<>(32);

        String peripheralName = register.getOwningPeripheralName();
        String fullRegisterName;

        if(isModeNameDefault(groupMode))
            fullRegisterName = peripheralName + "_" + getCVariableNameForRegister(register);
        else
            fullRegisterName = peripheralName + "_" + groupMode + "_" + getCVariableNameForRegister(register);

        String c99type = getC99TypeFromRegisterSize(register);
        CustomBitfield vecfield = null;
        int bitwidth = register.getSizeInBytes() * 8;
        int bfNextpos = 0;
        int vecNextpos = 0;
        List<String> bitfieldModes = register.getBitfieldModes();

        // Write out starting description text and opening to our union.
        //
        String captionStr = ("/* -------- " + fullRegisterName + " : (" + peripheralName + " Offset: ");
        captionStr += String.format("0x%02X", register.getBaseOffset());
        captionStr += ") (" + register.getRwAsString() + " " + bitwidth + ") " + register.getCaption();
        captionStr += " -------- */";
        writer.println(captionStr);
        writeNoAssemblyStart(writer);
        writer.println("typedef union {");

        // Fill our lists with bitfields and potential vecfields, including any gaps.
        //
        for(String bfMode : bitfieldModes) {
            bitfieldList.clear();

            for(AtdfBitfield bitfield : register.getBitfieldsByMode(bfMode)) {
                boolean endCurrentVecfield = true;
                boolean startNewVecfield = false;
                boolean gapWasPresent = addGapToBitfieldListIfNeeded(bitfieldList, bitfield.getLsb(), bfNextpos);

                // Check if we need to start a new vector field or continue one from this bitfield.
                // We need a vector field if this bitfield has a width of 1 and has a number at the end
                // of its name (basename != bitfield name).
                String bfBasename = Utils.getInstanceBasename(bitfield.getName());
                boolean bfHasNumberedName = !bfBasename.equals(bitfield.getName());
                if(bfHasNumberedName  &&  bitfield.getBitWidth() == 1) {
                    if(!gapWasPresent  &&  null != vecfield  &&  bfBasename.equals(vecfield.getName())) {
                        // Continue a current vecfield.
                        endCurrentVecfield = false;
                        vecfield.updateMask(bitfield.getMask());
                    } else {
                        // Start a new vecfield and output the old one.
                        startNewVecfield = true;
                    }
                }

                // Do we need to write out our current vector field and possibly create a new one?
                if(endCurrentVecfield) {
                    if(null != vecfield) {
                        addGapToBitfieldListIfNeeded(vecfieldList, vecfield.getLsb(), vecNextpos);
                        vecfieldList.add(vecfield);
                        vecNextpos = vecfield.getMsb()+1;
                    }

                    // We won't output vecfields if we have multiple bitfield modes.
                    if(bitfieldModes.size() <= 1  &&  startNewVecfield) {
                        vecfield = new CustomBitfield(bfBasename,
                                                      bitfield.getOwningRegisterName(),
                                                      bitfield.getCaption().replaceAll("\\d", "x"),
                                                      bitfield.getMask());
                    } else {
                        vecfield = null;
                    }
                }

                bitfieldList.add(bitfield);
                bfNextpos = bitfield.getMsb()+1;
            }

            // Add last vector field if one is present.
            if(null != vecfield) {
                addGapToBitfieldListIfNeeded(vecfieldList, vecfield.getLsb(), vecNextpos);
                vecfieldList.add(vecfield);
                vecNextpos = vecfield.getMsb()+1;
            }

            // Fill unused bits at end if needed.
            addGapToBitfieldListIfNeeded(bitfieldList, bitwidth, bfNextpos);
            addGapToBitfieldListIfNeeded(vecfieldList, bitwidth, vecNextpos);


            // Now, find duplicate vector fields and replace them with gaps of the same size.
            //
            for(int i = 0; i < vecfieldList.size()-1; ++i) {
                String iName = vecfieldList.get(i).getName();
                boolean duplicateFound = false;

                // An empty name means that this is a gap, so skip it for now.
                if(!iName.isEmpty()) {
                    for(int j = i+1; j < vecfieldList.size(); ++j) {
                        String jName = vecfieldList.get(j).getName();

                        if(iName.equals(jName)) {
                            vecfieldList.set(j, new CustomBitfield("", "", "", vecfieldList.get(j).getMask()));
                            duplicateFound = true;
                        }
                    }
                }

                if(duplicateFound) {
                    vecfieldList.set(i, new CustomBitfield("", "", "", vecfieldList.get(i).getMask()));
                }
            }

            // Next, we need to coalesce any adjacent gaps together into a single big gap.
            //
            for(int i = 0; i < vecfieldList.size()-1; ++i) {
                // An empty name means this is a gap, which is what we're looking for.
                if(vecfieldList.get(i).getName().isEmpty()) {
                    CustomBitfield bigGap = new CustomBitfield(vecfieldList.get(i));

                    int j = i+1;
                    while(j < vecfieldList.size()  &&  vecfieldList.get(j).getName().isEmpty()) {
                        bigGap.updateMask(vecfieldList.get(j).getMask());
                        vecfieldList.remove(j);
                    }

                    vecfieldList.set(i, bigGap);
                }
            }

            // If we just have one big gap, then there's no need to have any vecfields.
            if(1 == vecfieldList.size()  &&  vecfieldList.get(0).getName().isEmpty()) {
                vecfieldList.clear();
            }


            // Write out stuff for this mode.
            //
            writer.println("  struct {");
            for(AtdfBitfield bitfield : bitfieldList) {
                writeBitfieldDeclaration(writer, bitfield, c99type);
            }
            if(isModeNameDefault(bfMode)) {
                writer.println("  } bit;");
            } else {
                writer.println("  } " + bfMode + ";");
            }

            if(!vecfieldList.isEmpty()) {
                writer.println("  struct {");
                for(AtdfBitfield vec : vecfieldList) {
                    writeBitfieldDeclaration(writer, vec, c99type);
                }
                writer.println("  } vec;");
            }
        }

        // Finish writing our our register union.
        writer.println("  " + c99type + " reg;");
        writer.println("} " + getCTypeNameForRegister(register) + ";");
        writeNoAssemblyEnd(writer);
        writer.println();

        // Macros
        //
        writeStringMacro(writer, 
                         fullRegisterName + "_OFFSET",
                         String.format("(0x%02X)", register.getBaseOffset()),
                         fullRegisterName + " offset");
        writeStringMacro(writer, 
                         fullRegisterName + "_RESETVALUE",
                         String.format("_U_(0x%X)", register.getInitValue()),
                         fullRegisterName + " reset value");
        writeStringMacro(writer,
                         fullRegisterName + "_MASK",
                         String.format("_U_(0x%X)", register.getMask()),
                         fullRegisterName + " mask");

        writer.println();

        for(String bfMode : bitfieldModes) {
            for(AtdfBitfield bitfield : register.getBitfieldsByMode(bfMode)) {
                writeBitfieldMacros(writer, bitfield, bfMode, fullRegisterName, bitfield.getBitWidth() > 1);

                // Some bitfields use C macros to indicate what the different values of the bitfield
                // mean.  If this has those, then generate them here.
                List<AtdfValue> fieldValues = bitfield.getFieldValues();
                if(!fieldValues.isEmpty()) {
                    String valueMacroBasename = fullRegisterName + "_" + bitfield.getName() + "_";

                    // Create first set of macros containing option values.
                    for(AtdfValue val : fieldValues) {
                        String valueMacroName = valueMacroBasename + val.getName() + "_Val";
                        String valueMacroValue = "_U_(" + val.getValue() + ")";
                        String valueMacroCaption = val.getCaption();
                        writeStringMacro(writer, "  " + valueMacroName, valueMacroValue, valueMacroCaption);
                    }

                    // Now create second set which uses first set.
                    for(AtdfValue val : fieldValues) {
                        String optMacroName = valueMacroBasename + val.getName();
                        String valMacroName = optMacroName + "_Val";
                        String posMacroName = valueMacroBasename + "Pos";
                        writeStringMacro(writer, optMacroName, "(" + valMacroName + " << " + posMacroName + ")", "");
                    }
                }
            }
        }

        if(!vecfieldList.isEmpty()) {
            writer.println();

            for(AtdfBitfield vec : vecfieldList) {
                if(!vec.getName().isEmpty()) {
                    writeBitfieldMacros(writer, vec, null, fullRegisterName, true);
                }
            }
        }

        writer.println();
    }

    /* Output a C struct representing the layout of the given register group.  This will output
     * structs for all of this groups modes and an extra union of modes if the group has more than
     * one mode.
     */
    private void outputGroupDefinition(PrintWriter writer, AtdfRegisterGroup group) {
        List<String> memberModes = group.getMemberModes();

        for(String mode : memberModes) {
            long regNextOffset = 0;
            long regGapNumber = 1;

            List<AtdfRegister> members = group.getMembersByMode(mode);
            if(members.isEmpty()) {
                continue;
            }
            
            writeNoAssemblyStart(writer);
            writer.println("typedef struct {");

            for(AtdfRegister reg : members) {
                long regGap = reg.getBaseOffset() - regNextOffset;

                if(regGap > 0) {
                    String gapStr = "       RoReg8";
                    gapStr = Utils.padStringWithSpaces(gapStr, 36, 4);
                    gapStr += "Reserved" + regGapNumber + "[" + regGap + "];";

                    writer.println(gapStr);
                    ++regGapNumber;
                    regNextOffset = reg.getBaseOffset();
                }

                String regStr = "  " + getIOMacroFromRegisterAccess(reg) + " " + getCTypeNameForRegister(reg);
                regStr = Utils.padStringWithSpaces(regStr, 36, 4);

                regStr += getCVariableNameForRegister(reg);
                int count = reg.getNumRegisters();
                if(count > 1) {
                    regStr += "[" + count + "]";
                }
                regStr += ";";
                regStr = Utils.padStringWithSpaces(regStr, 48, 4);

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
                String padStr = "       RoReg8";
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

        // Output a union of modes if this group had multiple modes.
        if(memberModes.size() > 1) {
            writer.println("typedef union {");

            for(String mode : memberModes) {
                if(!group.getMembersByMode(mode).isEmpty()) {
                    String modeStr = "       " + getCTypeNameForGroup(group, mode);
                    modeStr = Utils.padStringWithSpaces(modeStr, 36, 4);
                    modeStr += mode + ";";
                    writer.println(modeStr);
                }
            }

            writer.println("} " + getCTypeNameForGroup(group, null) + ";");
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

    /* Output a header file that contains information on all of the peripheral instances on the 
     * device, such as some basic instance-specific macros and register addresses.
     */
    private void outputPeripheralInstancesHeader(TargetDevice target, List<AtdfPeripheral> peripheralList) 
                                    throws java.io.FileNotFoundException {
        // Output top-of-file stuff like license and include guards.
        String deviceName = getDeviceNameForHeader(target);
        String instancesMacro = "_" + deviceName.toUpperCase() + "_INSTANCES_";
        String instancesFilename = "instances/" + deviceName + ".h";
        String filepath = basepath_ + "/" + instancesFilename;

        try(PrintWriter instancesWriter = Utils.createUnixPrintWriter(filepath)) {
            outputLicenseHeader(instancesWriter, true);
            instancesWriter.println();
            instancesWriter.println("#ifndef " + instancesMacro);
            instancesWriter.println("#define " + instancesMacro);
            instancesWriter.println();

            for(AtdfPeripheral peripheral : peripheralList) {
                if(isArmInternalPeripheral(peripheral)) {
                    continue;
                }

                try {
                    for(AtdfInstance instance : peripheral.getAllInstances()) {
                        instancesWriter.println();
                        outputPeripheralInstanceRegisterMacros(instancesWriter, peripheral, instance);
                        instancesWriter.println();
                        outputPeripheralInstanceParameterMacros(instancesWriter, instance);
                        instancesWriter.println();
                    }
                } catch(SAXException ex) {
                    // Do nothing because this just might be an odd peripheral (see the constructor
                    // of AtdfInstance).
                }
            }

            // End-of-file stuff
            instancesWriter.println();
            instancesWriter.println("#endif /* " + instancesMacro + "*/");
        }

        // Include our new instances header in our main header file.
        writer_.println("#include \"" + instancesFilename + "\"");
        writer_.println();

    }

    /* Output macros representing all of the registers belonging to the given peripheral and whose
     * addresses are relative to the given instance.  The macros will be named for the instance, 
     * which in most cases is either the peripheral name or the name plus an instance number.
     */
    private void outputPeripheralInstanceRegisterMacros(PrintWriter writer, AtdfPeripheral peripheral,
                                                        AtdfInstance instance) {
        // The "base group" has the same name as the peripheral.
        AtdfRegisterGroup baseGroup = peripheral.getRegisterGroupByName(peripheral.getName());

        if(null == baseGroup)
            return;

        writer.print("/* ========== ");
        writer.print("Register definition for " + instance.getName() + " peripheral instance");
        writer.println(" ========== */");
        writeAssemblyStart(writer);
        outputRegisterGroupMacros(writer, baseGroup, 1, 0, peripheral, instance, true);
        writer.println("#else");
        outputRegisterGroupMacros(writer, baseGroup, 1, 0, peripheral, instance, false);
        writeAssemblyEnd(writer);
    }

    /* Output register macros for all registers belonging to the given group.  This will be
     * recursively called for groups that contain subgroups.  If 'isAssembler' is True, this will
     * output simpler macros that do not include a type.
     */
    private void outputRegisterGroupMacros(PrintWriter writer, AtdfRegisterGroup group, int numGroups,
                                           long groupOffset, AtdfPeripheral peripheral, AtdfInstance instance, 
                                           boolean isAssembler) {
        for(String mode : group.getMemberModes()) {
            for(int g = 0; g < numGroups; ++g) {
                for(AtdfRegister register : group.getMembersByMode(mode)) {
                    long regOffset = register.getBaseOffset();
                    int numRegs = register.getNumRegisters();

                    if(register.isGroupAlias()) {
                        AtdfRegisterGroup subgroup = peripheral.getRegisterGroupByName(register.getName());
                        if(null != subgroup) {
                            outputRegisterGroupMacros(writer, subgroup, numRegs, regOffset, peripheral,
                                                      instance, isAssembler);
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
                            name += getCVariableNameForRegister(register);

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
                            String addrStr;
                            if(isAssembler) {
                                addrStr = String.format("(0x%08X)", fullAddr);
                            } else {
                                String typeStr = getAtmelRegTypeFromRegister(register);
                                addrStr = String.format("(*(%-7s*)0x%08XUL)", typeStr, fullAddr);
                            }

                            writeStringMacro(writer, name, addrStr, register.getCaption());
                        }
                    }
                }
            }
        }
    }

    /* Output macros providing peripheral-specific information about the given instance.
     */
    private void outputPeripheralInstanceParameterMacros(PrintWriter writer, AtdfInstance instance) {
        writer.print("/* ========== ");
        writer.print("Instance parameters for " + instance.getName());
        writer.println(" ========== */");

        for(AtdfValue value : instance.getParameterValues()) {
            String name = instance.getName() + "_" + value.getName();
            writeStringMacro(writer, name, value.getValue(), value.getCaption());
        }
    }

    /* Return the C99 type to be used with the SFR based on its size.
     */
    private String getC99TypeFromRegisterSize(AtdfRegister reg) {
        int regSize = reg.getSizeInBytes();

        switch(regSize) {
            case 1:
                return "uint8_t";
            case 2:
                return "uint16_t";
            default:
                return "uint32_t";
        }
    }

    /* Return a C macro to indicate the access permission of a register.  This returns one of the
     * macros defined by Arm's CMSIS headers for this purpose ("__I " for read-only, "__O " for
     * write-only, or "__IO" for both).  This will return "    " (4 spaces) if the register
     * represents a group alias.
     */
    private String getIOMacroFromRegisterAccess(AtdfRegister reg) {
        if(reg.isGroupAlias()) {
            return "    ";
        } else {
            switch(reg.getRwAsInt()) {
                case AtdfRegister.REG_READ:
                    return "__I ";
                case AtdfRegister.REG_WRITE:
                    return "__O ";
                default:
                    return "__IO";
            }
        }
    }

    /* Return an Atmel-specific type, such as RwReg8 or RoReg16, based on the size and accessibility
     * of the register.  Returns an empty string if the given register is a group alias.
     */
    private String getAtmelRegTypeFromRegister(AtdfRegister reg) {
        if(reg.isGroupAlias()){
            return "";
        } else {
            String typeStr;

            switch(reg.getRwAsInt()) {
                case AtdfRegister.REG_READ:
                    typeStr = "RoReg";
                    break;
                case AtdfRegister.REG_WRITE:
                    typeStr = "WoReg";
                    break;
                default:
                    typeStr = "RwReg";
                    break;
            }

            switch(reg.getSizeInBytes()) {
                case 1:
                    return typeStr + "8";
                case 2:
                    return typeStr + "16";
                default:
                    return typeStr;    // 32-bit regs do not have a suffix
            }
        }
    }

    /* These next four are just convenience methods used to output idefs and endifs that block out
     * sections of header file based on wether or not an assembler is running.
     */
    private void writeNoAssemblyStart(PrintWriter writer) {
        writer.println("#ifndef __ASSEMBLY__");
    }

    private void writeNoAssemblyEnd(PrintWriter writer) {
        writer.println("#endif /* ifndef __ASSEMBLY__ */");
    }

    private void writeAssemblyStart(PrintWriter writer) {
        writer.println("#ifdef __ASSEMBLY__");
    }

    private void writeAssemblyEnd(PrintWriter writer) {
        writer.println("#endif /* ifdef __ASSEMBLY__ */");
    }


    /* Make a C macro of the form "#define <name>              <value>  / * <desc> * /"
     *
     * Note that value is padded out to 36 spaces minimum and that the spaces between the '/' and
     * '*' surrounding the description are not present in the output.
     */
    private String makeStringMacro(String name, String value, String desc) {
        String macro = "#define " + name;

        if(null != value  &&  !value.isEmpty()) {
            macro = Utils.padStringWithSpaces(macro, 36, 4);            
            macro += value;

            if(null != desc  &&  !desc.isEmpty()) {
                macro = Utils.padStringWithSpaces(macro, 48, 4);            
                macro += "/* " + desc + " */";
            }
        }

        return macro;
    }

    /* Like above, but also writes it using the given PrintWriter.
     */
    private void writeStringMacro(PrintWriter writer, String name, String value, String desc) {
        writer.println(makeStringMacro(name, value, desc));
    }

    /* Write a macro for the peripheral's version number as taken from the given ATDF document.
     */
    private void writePeripheralVersionMacro(PrintWriter writer, AtdfPeripheral peripheral) {
        String version = peripheral.getModuleVersion();

        if(version.isEmpty()) {
            return;
        }

        String macroName = "REV_" + peripheral.getName();

        if(Character.isDigit(version.charAt(0))) {
            // Version is probably in numeric form, eg. "1.0.2".
            String[] vals = version.split("\\.");
            long versionNum = 0;
            
            for(String v : vals) {
                versionNum = (versionNum << 4) | (Long.parseLong(v));
            }

            writeStringMacro(writer, macroName, String.format("0x%X", versionNum), "");
        } else {
            // Version is probably in letter form, eg. "ZJ".
            writeStringMacro(writer, macroName, version, "");
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


    /* Write a list of C macros that are used to access the given bitfield with each macro on its
     * own line.  This can generate two sets of macros depending on the state of 'extendedMacros'.
     * The normal set contains just a position macro and a mask macro.  Extended macros also contain
     * an additional function-like macro to set the value.
     */
    private void writeBitfieldMacros(PrintWriter writer, AtdfBitfield bitfield, String bitfieldMode,
                                        String fullRegisterName, boolean extendedMacros) {
        String qualifiedName = fullRegisterName + "<" + bitfield.getName() + ">";
        String baseMacroName;

        if(isModeNameDefault(bitfieldMode)) {
            baseMacroName = fullRegisterName + "_" + bitfield.getName();
        } else {
            baseMacroName = fullRegisterName + "_" + bitfieldMode + "_" + bitfield.getName();            
        }

        String posMacroName = baseMacroName + "_Pos";

        writeStringMacro(writer, posMacroName,
                                 "(" + bitfield.getLsb() + ")",
                                 qualifiedName + ": " + bitfield.getCaption());

        if(extendedMacros) {
            String maskMacroName = baseMacroName + "_Msk";
            String valueMacroName = baseMacroName + "(value)";

            writeStringMacro(writer, maskMacroName,
                                     String.format("_U_(0x%X)", bitfield.getMask()),
                                     "");
            writeStringMacro(writer, valueMacroName,
                                     String.format("(%s & ((value) << %s))", maskMacroName, posMacroName),
                                     "");
        } else {
            writeStringMacro(writer, baseMacroName,
                                     String.format("_U_(0x%X)", bitfield.getMask()),
                                     "");
        }
    }

    /* Add a blank CustomBitfield representing a gap to the given list if needed; that is, the start
     * of the next bitfield (second param) is greater than what was expected (third param).  Return 
     * True if a gap was added to the list or False otherwise.
     */
    private boolean addGapToBitfieldListIfNeeded(List<AtdfBitfield> list, int actualStart, int expectedStart) {
        boolean result = false;

        if(actualStart > expectedStart) {
            long gap = actualStart - expectedStart;
            long mask = ((1L << gap) - 1L) << expectedStart;
            list.add(new CustomBitfield("", "" ,"", mask));
            result = true;
        }

        return result;
    }

    /* We don't want to generate header files and macros for Arm peripherals (like NVIC, ETM, etc.)
     * because those are already handled by the Arm CMSIS headers.  This will return True if the
     * given periphral is an Arm one instead of an Atmel one.
     */
    private boolean isArmInternalPeripheral(AtdfPeripheral peripheral) {
        // Arm peripherals do not have a module ID, so use this for now.
        return peripheral.getModuleId().isEmpty();
    }

    /* Return True if the given mode name refers to the default mode, which is the mode that every
     * register or bitfield can have.
    */
    private boolean isModeNameDefault(String modeName) {
        return null == modeName  ||  modeName.isEmpty()  ||  modeName.equals("DEFAULT");
    }


    /* Create a name for the group suitable as a C variable name in the resulting header file.  The 
     * result will be the group name with the first letter capitalized and the rest lower-case.
     */
    private String getCVariableNameForGroup(AtdfRegisterGroup group) {
        String name = group.getName();
        String owner = group.getOwningPeripheralName();

        // Some groups in the ATDF doc start with the owner name, so remove that.
        if(name.startsWith(owner)) {
            name = name.substring(owner.length());

            // In case the name in the ATDF doc was something like "OWNER_REGISTER".
            if(name.startsWith("_")) {
                name = name.substring(1);
            }
        }

        return Utils.makeOnlyFirstLetterUpperCase(name);
    }

    /* Create a name for the group suitable as a C type or struct name in the resulting header file.
     * The type incorporates the owning peripheral name and the mode name if present.  The result
     * will be either "OwnerModeGroup" or "OwnerGroup" if 'mode' is null or empty.
     */
    private String getCTypeNameForGroup(AtdfRegisterGroup group, String mode) {
        String name = getCVariableNameForGroup(group);
        String owner = group.getOwningPeripheralName();

        owner = Utils.makeOnlyFirstLetterUpperCase(owner);

        if(isModeNameDefault(mode)) {
            return owner + name;            
        } else {
            mode = Utils.makeOnlyFirstLetterUpperCase(mode);
            return owner + mode + name;
        }
    }

    /* Create a name for the register suitable as a C variable name in the resulting header file.
     * If this register is a group alias, the name will be formatted like a group name; otherwise, 
     * it will be the register name in all caps.
     */
    private String getCVariableNameForRegister(AtdfRegister reg) {
        String name = reg.getName();
        String owner = reg.getOwningPeripheralName();

        // Some register in the ATDF doc start with the owner name, so remove that.
        if(name.startsWith(owner)) {
            name = name.substring(owner.length());

            // In case the name in the ATDF doc was something like "OWNER_REGISTER".
            if(name.startsWith("_")) {
                name = name.substring(1);
            }
        }

        if(reg.isGroupAlias()) {
            return Utils.makeOnlyFirstLetterUpperCase(name);
        } else {
            return name.toUpperCase();
        }
    }

    /* Create a name for the register suitable as a C type or struct name in the resulting header 
     * file.  The type incorporates the owning peripheral name and the mode name if this register
     * has one.  If this is a group alias, then the name will be formatted like a group name.  If
     * this is not a group alias and does not have a mode, then the result will look like 
     * "OWNER_REGISTER_Type".  If this does have a mode, the result will be formatted as
     * "OWNER_MODE_REGISTER_Type".
     */
    private String getCTypeNameForRegister(AtdfRegister reg) {
        String name = getCVariableNameForRegister(reg);
        String owner = reg.getOwningPeripheralName();

        if(reg.isGroupAlias()) {
            owner = Utils.makeOnlyFirstLetterUpperCase(owner);
            return owner + name;
        } else {
            String mode = reg.getMode();

            if(mode.isEmpty()) {
                return owner.toUpperCase() + "_" + name + "_Type";
            } else {
                return owner.toUpperCase() + "_" + mode.toUpperCase() + "_" + name + "_Type";                
            }
        }
    }
}
