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
import java.util.HashSet;
import java.util.List;
import org.xml.sax.SAXException;

/**
 * A subclass of the HeaderFileBuilder that handles ARM Cortex-M devices.  This generates "legacy"
 * header files that were used when Atmel was still independent.  Microchip has since updated the
 * header files to be compatible with Harmony 3 and those are generally simplified compared to 
 * these ones.
 */
public class CortexMLegacyHeaderFileGenerator extends HeaderFileGenerator {

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


    public CortexMLegacyHeaderFileGenerator(String basepath) {
        super(basepath);
    }

    @Override
    public void generate(TargetDevice target) 
                                    throws java.io.FileNotFoundException, SAXException {
        AtdfDoc atdfDoc = target.getAtdfDocument();

        createNewHeaderFile(target);

        List<AtdfPeripheral> peripherals = atdfDoc.getAllPeripherals();
        AtdfDevice device = atdfDoc.getDevice();
        List<AtdfValue> basicDeviceParams = device.getBasicParameterValues();

        outputLicenseHeader(writer_, true);
        outputIncludeGuardStart(target);
        outputExternCStart();
        outputPreamble();
        outputInterruptDefinitions(target);
        outputBasicCpuParameters(target.getCpuName(), basicDeviceParams);
        outputCmsisDeclarations(target.getCpuName());
        outputPeripheralDefinitionHeaders(peripherals);
        outputPeripheralInstancesHeader(target, peripherals);
        outputPeripheralModuleIdMacros(peripherals);
        outputBaseAddressMacros(peripherals);
        outputMemoryMapMacros(atdfDoc.getDevice());
        outputDeviceSignatureMacros(device);
        outputElectricalParameterMacros(device);
        outputExternCEnd();
        outputIncludeGuardEnd(target);

        closeHeaderFile();
    }


    /* Output the opening "#ifndef...#define" sequence of an include guard for this header file.
     */
    private void outputIncludeGuardStart(TargetDevice target) {
        writer_.println("#ifndef _INCLUDE_" + target.getDeviceNameForMacro() + "_H_");
        writer_.println("#define _INCLUDE_" + target.getDeviceNameForMacro() + "_H_");
        writer_.println();
    }

    /* Output the closing "#endif" of an include guard for this header file.
     */
    private void outputIncludeGuardEnd(TargetDevice target) {
        writer_.println("#endif  /* _INCLUDE_" + target.getDeviceNameForMacro() + "_H_ */");
    }

    /* Output the opening sequence of macros for C linkage.
     */
    private void outputExternCStart() {
        writer_.println("#ifdef __cplusplus");
        writer_.println("extern \"C\" {");
        writer_.println("#endif");
        writer_.println();
    }

    /* Output the closing sequence of macros for C linkage.
     */
    private void outputExternCEnd() {
        writer_.println("#ifdef __cplusplus");
        writer_.println("} /* extern \"C\" */");
        writer_.println("#endif");
        writer_.println();
    }

    /* Output the start of the file that includes some typedefs and macro definitions used by the 
     * rest of the file and the files it includes.
     */
    private void outputPreamble() {
        writeNoAssemblyStart(writer_);
        writer_.println("#include <stdint.h>");
        writer_.println("typedef volatile const uint32_t RoReg;   /* Read only 32-bit register */");
        writer_.println("typedef volatile const uint16_t RoReg16; /* Read only 16-bit register */");
        writer_.println("typedef volatile const uint8_t  RoReg8;  /* Read only  8-bit register */");
        writer_.println("typedef volatile       uint32_t WoReg;   /* Write only 32-bit register */");
        writer_.println("typedef volatile       uint16_t WoReg16; /* Write only 16-bit register */");
        writer_.println("typedef volatile       uint8_t  WoReg8;  /* Write only  8-bit register */");
        writer_.println("typedef volatile       uint32_t RwReg;   /* Read-Write 32-bit register */");
        writer_.println("typedef volatile       uint16_t RwReg16; /* Read-Write 16-bit register */");
        writer_.println("typedef volatile       uint8_t  RwReg8;  /* Read-Write  8-bit register */");
        writeNoAssemblyEnd(writer_);
        writer_.println();
        writer_.println("#if !defined(SKIP_INTEGER_LITERALS)");
        writer_.println("#if defined(_U_) || defined(_L_) || defined(_UL_)");
        writer_.println("  #error \"Integer Literals macros already defined elsewhere\"");
        writer_.println("#endif");
        writer_.println();
        writeNoAssemblyStart(writer_);
        writer_.println("/* Macros that deal with adding suffixes to integer literal constants for C/C++ */");
        writer_.println("#define _U_(x)         x ## U            /* C code: Unsigned integer literal constant value */");
        writer_.println("#define _L_(x)         x ## L            /* C code: Long integer literal constant value */");
        writer_.println("#define _UL_(x)        x ## UL           /* C code: Unsigned Long integer literal constant value */");
        writer_.println("#else /* Assembler */");
        writer_.println("#define _U_(x)         x                 /* Assembler: Unsigned integer literal constant value */");
        writer_.println("#define _L_(x)         x                 /* Assembler: Long integer literal constant value */");
        writer_.println("#define _UL_(x)        x                 /* Assembler: Unsigned Long integer literal constant value */");
        writeNoAssemblyEnd(writer_);
        writer_.println("#endif /* SKIP_INTEGER_LITERALS */");
        writer_.println();
    }


    /* Output all of the structures needed to define all of the interrupt vectors in the main header
     * file.  These are an enumeration of interrupts, the interrupt vector table typedef, and the
     * declarations for the handler functions().
     */
    private void outputInterruptDefinitions(TargetDevice target) {
        InterruptList interruptList = new InterruptList(target.getPic());

        writeHeaderSectionHeading(writer_, "Interrupt Vector Definitions");
        writeNoAssemblyStart(writer_);
        outputInterruptEnum(interruptList);
        outputInterruptVectorTableTypedef(interruptList);
        outputInterruptHandlerDeclarations(interruptList);
        writeNoAssemblyEnd(writer_);
        writer_.println();
    }

    /* Output a C enum whose values correspond to the interrupt requests on the device.  This will
     * output to the main header file using this object's writer.
     */
    private void outputInterruptEnum(InterruptList interruptList) {
        writer_.println("typedef enum IRQn");
        writer_.println("{");

        for(InterruptList.Interrupt vector : interruptList.getInterruptVectors()) {
            String irqString = "  " + vector.getName() + "_IRQn";
            irqString = Utils.padStringWithSpaces(irqString, 32, 4);
            irqString += " = " + vector.getIntNumber() + ",";
            irqString = Utils.padStringWithSpaces(irqString, 40, 4);
            irqString += "/* " + vector.getDescription();

            if(!vector.getOwningPeripheral().isEmpty()) {
                irqString += " (" + vector.getOwningPeripheral() + ") */";
            } else {
                irqString += " */";
            }

            writer_.println(irqString);
        }

        writer_.println();
        writer_.println("  PERIPH_COUNT_IRQn              = " + (interruptList.getLastVectorNumber()+1));
        writer_.println("} IRQn_Type;");
        writer_.println();
    }

    /* Output the typedef of the interrupt vector table, which is a struct of void pointers.  Note
     * that this is just the typedef and the table is actually defined in the C startup file for this
     * device.  This will output to the main header file using this object's writer.
     */
    private void outputInterruptVectorTableTypedef(InterruptList interruptList) {
        writer_.println("typedef struct _DeviceVectors");
        writer_.println("{");
        writer_.println("  void *pvStack;                            /* Initial stack pointer */");
        writer_.println();

        int nextVectorNumber = 99999;

        for(InterruptList.Interrupt vector : interruptList.getInterruptVectors()) {
            int vectorNumber = vector.getIntNumber();

            while(vectorNumber > nextVectorNumber) {
                // Fill in any gaps we come across.
                if(nextVectorNumber < 0) {
                    writer_.println("  void *pvReservedM" + (-1 * nextVectorNumber) + ";");
                } else {
                    writer_.println("  void *pvReserved" + nextVectorNumber + ";");
                }

                ++nextVectorNumber;
            }

            String vectorString = "  void *pfn" + vector.getName() + "_Handler;";
            String descString = String.format("/* %3d %s */", vectorNumber, vector.getDescription());

            writer_.println(Utils.padStringWithSpaces(vectorString, 44, 4) + descString);

            nextVectorNumber = vectorNumber + 1;
        }

        writer_.println("} DeviceVectors;");
        writer_.println();
    }

    /* Output one function declarations for each interrupt handler.  The default handlers would be
     * defined in the C startup file for this device and the user's firmware would override these
     * to handler interrupts.  This will output to the main header file using this object's writer.
     */
    private void outputInterruptHandlerDeclarations(InterruptList interruptList) {
        for(InterruptList.Interrupt vector : interruptList.getInterruptVectors()) {
            writer_.println("void " + vector.getName() + "_Handler(void);");
        }

        writer_.println();
    }


    private void outputBasicCpuParameters(String cpuName, List<AtdfValue> paramList) {
        cpuName = Utils.makeOnlyFirstLetterUpperCase(cpuName);

        writeHeaderSectionHeading(writer_, "Basic config parameters for " + cpuName);

        for(AtdfValue param : paramList) {
            writeStringMacro(writer_, param.getName(), param.getValue(), param.getCaption());
        }

        writer_.println();
    }


    /* CMSIS is Arm's common interface and function API for all Cortex-based microcontrollers.  This
     * will write out declarations to the main header file related to CMSIS.
    */
    private void outputCmsisDeclarations(String cpuName) {
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


    /* Each peripheral has a header file filled with structs and macros describing its member SFRs
     * and layout.  This will output said header files for this device, skipping over ones that have
     * been already output from previous calls, and write the include directives to the main device 
     * file needed to include these files.
     */
    private void outputPeripheralDefinitionHeaders(List<AtdfPeripheral> peripheralList) 
                                    throws java.io.FileNotFoundException, SAXException {
        writeHeaderSectionHeading(writer_, "Device-specific Peripheral Definitions");

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
        writeHeaderSectionHeading(writer_, "Device-specific Peripheral Instance Definitions");

        String deviceName = getDeviceNameForHeader(target);
        String instancesMacro = "_" + deviceName.toUpperCase() + "_INSTANCES_";
        String instancesFilename = "instances/" + deviceName + ".h";
        String filepath = basepath_ + "/" + instancesFilename;

        try(PrintWriter instancesWriter = Utils.createUnixPrintWriter(filepath)) {
            // Output top-of-file stuff like license and include guards.
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


    /* Output macros that provide module ID values for the peripherals on the device.  These are
     * used usually for a power control module on the device to enable and disable the peripheral.
     */
    private void outputPeripheralModuleIdMacros(List<AtdfPeripheral> peripheralList) {
        writeHeaderSectionHeading(writer_, "Peripheral ID Macros");
        int maxId = -1000;

        for(AtdfPeripheral peripheral : peripheralList){
            if(isArmInternalPeripheral(peripheral)) {
                continue;
            }

            try {
                for(AtdfInstance instance : peripheral.getAllInstances()) {
                    int id = instance.getInstanceId();

                    if(id >= 0) {
                        writeStringMacro(writer_, "ID_" + instance.getName(), Integer.toString(id), null);

                        if(id > maxId)
                            maxId = id;
                    }
                }
            } catch(SAXException ex) {
                // Do nothing for now because some peripherals do not have a publicly-documented
                // instance (crypto peripherals are like this, for example).
            }
        }

        writeStringMacro(writer_, "ID_PERIPH_COUNT", Integer.toString(maxId+1), null);
        writer_.println();
    }


    /* Output macros to provide the base address of peripherals along with information on the
     * instances of each peripheral.
     */
    private void outputBaseAddressMacros(List<AtdfPeripheral> peripheralList){
        writeHeaderSectionHeading(writer_, "Peripheral Base Address Macros");

        writeAssemblyStart(writer_);
        outputBaseAddressMacrosForAssembly(peripheralList);
        writer_.println("#else /* !__ASSEMBLER__ */");
        outputBaseAddressMacrosForC(peripheralList);
        writeAssemblyEnd(writer_);
        writer_.println();
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


    /* Write out macros that provide information on how the memory map is laid out on the device,
     * such as page size and the location of different memory spaces/
     */
    private void outputMemoryMapMacros(AtdfDevice device) {
        writeHeaderSectionHeading(writer_, "Memory Segment Macros");

        List<AtdfMemSegment> memSegmentList = device.getMemorySegments();
        for(AtdfMemSegment memSegment : memSegmentList) {
            String name = memSegment.getName();
            long startAddr = memSegment.getStartAddress();
            long pageSize = memSegment.getPageSize();
            long totalSize = memSegment.getTotalSize();

            writeStringMacro(writer_,
                             name + "_ADDR", 
                             "_UL_(0x" + Long.toHexString(startAddr) + ")",
                             name + " base address");
            writeStringMacro(writer_,
                             name + "_SIZE",
                             "_UL_(0x" + Long.toHexString(totalSize) + ")",
                             name + " size");

            if(pageSize > 0) {
                writeStringMacro(writer_,
                                 name + "_PAGE_SIZE",
                                 Long.toString(pageSize),
                                 name + " page size");
                writeStringMacro(writer_,
                                 name + "_NB_OF_PAGES",
                                 Long.toString(totalSize / pageSize),
                                 name + " number of pages");
            }

            writer_.println();
        }
    }


    /* Output macros for chip ID info if they are available for the given device.
     */
    private void outputDeviceSignatureMacros(AtdfDevice device) {
        writeHeaderSectionHeading(writer_, "Device Signature Macros");

        List<AtdfValue> sigList = device.getSignatureParameterValues();

        if(sigList.size() > 0) {
            for(AtdfValue sig : sigList) {
                writeStringMacro(writer_, sig.getName(), "_UL_(" + sig.getValue() + ")", sig.getCaption());
            }
        } else {
            writer_.println("/* <No signature macros provided for this device.> */");
        }

        writer_.println();
    }


    /* Output macros for electrical characteristics if they are available for the given device.
     */
    private void outputElectricalParameterMacros(AtdfDevice device) {
        writeHeaderSectionHeading(writer_, "Device Electrical Parameter Macros");

        List<AtdfValue> paramList = device.getElectricalParameterValues();

        if(paramList.size() > 0) {
            for(AtdfValue param : paramList) {
                writeStringMacro(writer_, param.getName(), "_UL_(" + param.getValue() + ")", param.getCaption());
            }
        } else {
            writer_.println("/* <No electrical parameter macros provided for this device.> */");
        }

        writer_.println();
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
        // Arm peripherals are located at address 0xE0000000 and above.
        boolean isArmPeripheral = true;

        try {
            isArmPeripheral = (peripheral.getInstance(0).getBaseAddress() >= 0xE0000000L);
        } catch(SAXException ex) {
            // Do nothing for now...
        }

        return isArmPeripheral;
    }

    /* Return True if the given mode name refers to the default mode, which is the mode that every
     * register or bitfield can have.
     */
    private boolean isModeNameDefault(String modeName) {
        return null == modeName  ||  modeName.isEmpty()  ||  modeName.equals("DEFAULT");
    }

    /* Write a comment that could be used to call out a new portion of the header file as a section
     * of particular importance on its own, such as a section for interrupt or a memory map.
     */
    private void writeHeaderSectionHeading(PrintWriter writer, String heading) {
        writer.println("/******");
        writer.println(" * " + heading);
        writer.println(" */");
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
