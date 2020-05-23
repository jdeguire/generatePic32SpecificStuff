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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.xml.sax.SAXException;

/**
 * A subclass of the HeaderFileBuilder that handles ARM Cortex-M devices.  This generates new style
 * header files that Microchip generates for use with Harmony 3 and XC32 starting with v2.40.  These
 * header files are generally simplified compared to the older style that Atmel generated in that
 * these do not have structs for each register, only macros.
 */
public class CortexMHeaderFileGenerator extends HeaderFileGenerator {

    private final HashSet<String> peripheralFiles_ = new HashSet<>(20);


    public CortexMHeaderFileGenerator(String basepath) {
        super(basepath);
    }

    @Override
    public void generate(TargetDevice target) 
                                    throws java.io.FileNotFoundException, SAXException {
        AtdfDoc atdfDoc = target.getAtdfDocument();

        createNewHeaderFile(target);

        List<AtdfPeripheral> peripherals = atdfDoc.getAllPeripherals();
        List<String> pinNames = atdfDoc.getPortPinNames();
        AtdfDevice device = atdfDoc.getDevice();
        List<AtdfValue> basicDeviceParams = device.getBasicParameterValues();

        outputLicenseHeaderMicrochipStandard(writer_);
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
        outputPioDefinitionHeader(target, pinNames, peripherals);
        outputMemoryMapMacros(atdfDoc.getDevice());
        outputDeviceSignatureMacros(device);
        outputElectricalParameterMacros(device);
        outputEventMacros(device);
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
        writer_.println("  PERIPH_MAX_IRQn                = " + interruptList.getLastVectorNumber());
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
        writer_.println("#if defined USE_CMSIS_INIT");
        writer_.println("extern uint32_t SystemCoreClock;   /* System (Core) Clock Frequency */");
        writer_.println("void SystemInit(void);");
        writer_.println("void SystemCoreClockUpdate(void);");
        writer_.println("#endif /* USE_CMSIS_INIT */");
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
                    outputLicenseHeaderMicrochipStandard(peripheralWriter);
                    peripheralWriter.println();
                    peripheralWriter.println("#ifndef _" + peripheralMacro + "_COMPONENT_");
                    peripheralWriter.println("#define _" + peripheralMacro + "_COMPONENT_");
                    peripheralWriter.println();
                    writeStringMacro(peripheralWriter, peripheralMacro, "", "");
                    writePeripheralVersionMacro(peripheralWriter, peripheral);
                    peripheralWriter.println();

                    // Output definitions for each register in all of the groups.
                    for(AtdfRegisterGroup registerGroup : peripheral.getAllRegisterGroups()) {
                        String prefix = registerGroup.getMemberNamePrefix();

                        for(String mode : registerGroup.getMemberModes()) {
                            for(AtdfRegister register : registerGroup.getMembersByMode(mode)) {
                                if(!register.isGroupAlias())
                                    outputRegisterDefinition(peripheralWriter, register, mode, prefix);
                            }
                        }
                    }

                    // Now output definitions for the groups themselves
                    for(AtdfRegisterGroup registerGroup : peripheral.getAllRegisterGroups()) {
                        outputGroupDefinition(peripheralWriter, registerGroup, registerGroup.getMemberNamePrefix());
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

    /* Output a bunch of C macros that can be used to access the fields within the given register.  
     * This will also output some descriptive text as given in the ATDF document associated with
     * this device.
     */
    private void outputRegisterDefinition(PrintWriter writer, 
                                          AtdfRegister register,
                                          String groupMode,
                                          String regNamePrefix)
                                            throws SAXException {
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

    /* Step through the list of bitfields and coalesce related adjacent single-bit fields into
     * larger multi-bit fields.  The Atmel headers called the structs that contained these "vec" and
     * so this generator uses that name; hence, "vecfield".
     */
    private List<AtdfBitfield> getVecfieldsFromBitfields(List<AtdfBitfield> bitfieldList) {
        ArrayList<AtdfBitfield> vecfieldList = new ArrayList<>(8);
        VecField vecfield = null;
        int bfNextpos = 0;

        for(AtdfBitfield bitfield : bitfieldList) {
            boolean endCurrentVecfield = true;
            boolean startNewVecfield = false;
            boolean gapWasPresent = (bitfield.getLsb() > bfNextpos);

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
                    vecfieldList.add(vecfield);
                }

                if(startNewVecfield) {
                    vecfield = new VecField(bfBasename,
                                            bitfield.getOwningRegisterName(),
                                            bitfield.getCaption().replaceAll("\\d", "x"),
                                            bitfield.getMask());
                } else {
                    vecfield = null;
                }
            }

            bfNextpos = bitfield.getMsb()+1;
        }

        // Add last vector field if one is present.
        if(null != vecfield) {
            vecfieldList.add(vecfield);
        }

        // Now, find and remove duplicate vector fields.
        //
        if(vecfieldList.size() > 1) {
            // Walk backwards in these so we can delete stuff after our current position without
            // invalidating our indices.
            for(int i = vecfieldList.size()-2; i >= 0; --i) {
                String iName = vecfieldList.get(i).getName();
                boolean duplicateFound = false;

                for(int j = vecfieldList.size()-1; j > i; --j) {
                    String jName = vecfieldList.get(j).getName();

                    if(iName.equals(jName)) {
                        // Just set a flag in case we find multiple duplicates.
                        duplicateFound = true;
                        vecfieldList.remove(j);
                    }
                }

                if(duplicateFound) {
                    vecfieldList.remove(i);
                }
            }
        }

        return vecfieldList;
    }

    /* Output a C struct representing the layout of the given register group.  This will output
     * structs for all of this group's modes and an extra union of modes if the group has more than
     * one mode.
     */
    private void outputGroupDefinition(PrintWriter writer,
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
            outputLicenseHeaderMicrochipStandard(instancesWriter);
            instancesWriter.println();
            instancesWriter.println("#ifndef " + instancesMacro);
            instancesWriter.println("#define " + instancesMacro);
            instancesWriter.println();

            outputPeripheralInstanceWrapperMacroDefinitions(instancesWriter);

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

    /* Output macros that can be used to allow the peripheral instance macros to be used in either
     * C or assembly without having to duplicate them in the file
     */
    private void outputPeripheralInstanceWrapperMacroDefinitions(PrintWriter writer) {
        writeNoAssemblyStart(writer);
        writeStringMacro(writer, "_RoReg32_(x)", "(*(volatile const uint32_t *)x##UL)", null);
        writeStringMacro(writer, "_RoReg16_(x)", "(*(volatile const uint16_t *)x##UL)", null);
        writeStringMacro(writer, "_RoReg8_(x)", "(*(volatile const uint8_t *)x##UL)", null);
        writeStringMacro(writer, "_WoReg32_(x)", "(*(volatile uint32_t *)x##UL)", null);
        writeStringMacro(writer, "_WoReg16_(x)", "(*(volatile uint16_t *)x##UL)", null);
        writeStringMacro(writer, "_WoReg8_(x)", "(*(volatile uint8_t *)x##UL)", null);
        writeStringMacro(writer, "_RwReg32_(x)", "(*(volatile uint32_t *)x##UL)", null);
        writeStringMacro(writer, "_RwReg16_(x)", "(*(volatile uint16_t *)x##UL)", null);
        writeStringMacro(writer, "_RwReg8_(x)", "(*(volatile uint8_t *)x##UL)", null);
        writer.println("#else /* Assembly */");
        writeStringMacro(writer, "_RoReg32_(x)", "(x)", null);
        writeStringMacro(writer, "_RoReg16_(x)", "(x)", null);
        writeStringMacro(writer, "_RoReg8_(x)", "(x)", null);
        writeStringMacro(writer, "_WoReg32_(x)", "(x)", null);
        writeStringMacro(writer, "_WoReg16_(x)", "(x)", null);
        writeStringMacro(writer, "_WoReg8_(x)", "(x)", null);
        writeStringMacro(writer, "_RwReg32_(x)", "(x)", null);
        writeStringMacro(writer, "_RwReg16_(x)", "(x)", null);
        writeStringMacro(writer, "_RwReg8_(x)", "(x)", null);
        writeNoAssemblyEnd(writer);
        writer.println();
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
        outputRegisterGroupMacros(writer, baseGroup, 1, 0, peripheral, instance);
    }

    /* Output register macros for all registers belonging to the given group.  This will be
     * recursively called for groups that contain subgroups.  If 'isAssembler' is True, this will
     * output simpler macros that do not include a type.
     */
    private void outputRegisterGroupMacros(PrintWriter writer, AtdfRegisterGroup group, int numGroups,
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

    /* Return the wrapper macro to use for the given register based on its size and accessibility.
     */
    private String getPeripheralInstanceWrapperMacro(AtdfRegister reg) {
        String macroStr;

        switch(reg.getRwAsInt()) {
            case AtdfRegister.REG_READ:
                macroStr = "_RoReg";
                break;
            case AtdfRegister.REG_WRITE:
                macroStr = "_WoReg";
                break;
            default:
                macroStr = "_RwReg";
                break;
        }

        switch(reg.getSizeInBytes()) {
            case 1:
                return macroStr + "8_";
            case 2:
                return macroStr + "16_";
            default:
                return macroStr + "32_";
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
                        String idStr = "(" + Integer.toString(id) + ")";
                        writeStringMacro(writer_, "ID_" + instance.getName(), idStr, null);

                        if(id > maxId)
                            maxId = id;
                    }
                }
            } catch(SAXException ex) {
                // Do nothing for now because some peripherals do not have a publicly-documented
                // instance (crypto peripherals are like this, for example).
            }
        }

        writeStringMacro(writer_, "ID_PERIPH_MAX", "(" + Integer.toString(maxId) + ")", null);
        writeStringMacro(writer_, "ID_PERIPH_COUNT", "(" + Integer.toString(maxId+1) + ")", null);
        writer_.println();
    }


    /* Output macros to provide the base address of peripheral instances.
     */
    private void outputBaseAddressMacros(List<AtdfPeripheral> peripheralList){
        writeHeaderSectionHeading(writer_, "Peripheral Base Address Macros");

        writeNoAssemblyStart(writer_);
        outputInstanceRegisterAddressMacros(peripheralList);
        writeNoAssemblyEnd(writer_);
        writer_.println();
        outputBareBaseAddressMacros(peripheralList);
        writer_.println();
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


    /* Output a header file that contains information on all of the peripheral instances on the 
     * device, such as some basic instance-specific macros and register addresses.
     */
    private void outputPioDefinitionHeader(TargetDevice target, List<String> pinNames, 
                                           List<AtdfPeripheral> peripheralList) 
                                                throws java.io.FileNotFoundException {
        writeHeaderSectionHeading(writer_, "Device-specific Port IO Definitions");

        String deviceName = getDeviceNameForHeader(target);
        String pioMacro = "_" + deviceName.toUpperCase() + "_PIO_";
        String pioFilename = "pio/" + deviceName + ".h";
        String filepath = basepath_ + "/" + pioFilename;

        try(PrintWriter pioWriter = Utils.createUnixPrintWriter(filepath)) {
            // Output top-of-file stuff like license and include guards.
            outputLicenseHeaderApache(pioWriter);
            pioWriter.println();
            pioWriter.println("#ifndef " + pioMacro);
            pioWriter.println("#define " + pioMacro);
            pioWriter.println();

            sortPortPinNames(pinNames);

            for(String pin : pinNames) {
                outputPioPortPinMacros(pioWriter, pin);
            }

            for(AtdfPeripheral peripheral : peripheralList) {
                try {
                    for(AtdfInstance instance : peripheral.getAllInstances()) {
                        pioWriter.println();
                        outputPioPeripheralInstanceMacros(pioWriter, instance);
                    }
                } catch(SAXException ex) {
                    // Do nothing because this just might be an odd peripheral (see the constructor
                    // of AtdfInstance).
                }
            }

            // End-of-file stuff
            pioWriter.println();
            pioWriter.println("#endif /* " + pioMacro + "*/");
        }

        // Include our new instances header in our main header file.
        writer_.println("#include \"" + pioFilename + "\"");
        writer_.println();

    }

    /* Output macros that can be used to conveniently access the given port pin.
     */
    private void outputPioPortPinMacros(PrintWriter writer, String pin) {
        int portNum = (int)pin.charAt(1) - (int)'A';
        int pinNum = Integer.parseInt(pin.substring(2));

        writeStringMacro(writer, 
                         "PIN_" + pin,
                         "(" + (32*portNum + pinNum) + ")",
                         "Pin number for " + pin);
        writeStringMacro(writer,
                         "PORT_" + pin,
                         "(_UL_(1) << " + pinNum + ")",
                         "Port mask for " + pin);
    }

    /* Output macros that seem to indicate what device pins can be used by the given peripheral 
     * instance and might be used to set up pin muxes.
     */
    private void outputPioPeripheralInstanceMacros(PrintWriter writer, AtdfInstance instance) {
        String instName = instance.getName();

        writer.println("/*********** Pio macros for peripheral instance " + instance.getName() + 
                        " ***********/");

        for(AtdfSignal signal : instance.getSignals()) {
            String pad = signal.getPad();
            String function = signal.getFunction();
            String sigGroup = signal.getGroup();
            String sigIndex = signal.getIndex();

            // This comes up in the GPIO peripherals because they're always active--no mux setting
            // needed.  Just skip over these.
            if(function.equalsIgnoreCase("default")) {
                continue;
            }

            try {
                int pinNum = Integer.parseInt(pad.substring(2));
                int portNum = (int)pad.charAt(1) - (int)'A';
                int pinMux = (int)function.charAt(0) - (int)'A';

                String macroSuffix = pad + function + "_" + instName + "_" + sigGroup + sigIndex;
                String pinMacro = "PIN_" + macroSuffix;
                String muxMacro = "MUX_" + macroSuffix;
                String caption = instName + " signal: " + sigGroup + " on " + pad + " mux " + function;

                writeStringMacro(writer,
                                 pinMacro,
                                 "_L_(" + (32*portNum + pinNum) + ")",
                                 caption);
                writeStringMacro(writer,
                                 muxMacro,
                                 "_L_(" + pinMux + ")",
                                 null);
                writeStringMacro(writer,
                                 "PINMUX_" + macroSuffix,
                                 "((" + pinMacro + " << 16) | " + muxMacro + ")",
                                 null);
                writeStringMacro(writer,
                                 "PORT_" + macroSuffix,
                                 "(_UL_(1) << " + pinNum + ")",
                                 null);
                writeStringMacro(writer,
                                 "PIO_" + macroSuffix,
                                 "(_UL_(1) << " + pinNum + ")",
                                 null);
                writer.println();
            } catch(NumberFormatException nfe) {
                // This can happen because non-ports, like RESET, might be called out in a signal.
                // In this case, just skip over that signal.
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

        if(!sigList.isEmpty()) {
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

        if(!paramList.isEmpty()) {
            for(AtdfValue param : paramList) {
                writeStringMacro(writer_, param.getName(), "_UL_(" + param.getValue() + ")", param.getCaption());
            }
        } else {
            writer_.println("/* <No electrical parameter macros provided for this device.> */");
        }

        writer_.println();
    }

    /* Output macros used for the event system on the device if the device has one.
     */
    private void outputEventMacros(AtdfDevice device) {
        // Generators
        //
        writeHeaderSectionHeading(writer_, "Device Event Generator Macros");
        
        List<AtdfEvent> generatorList = device.getEventGenerators();

        if(!generatorList.isEmpty()) {
            for(AtdfEvent event : generatorList) {
                writeStringMacro(writer_,
                                 "EVENT_ID_GEN_" + event.getName(),
                                 "(" + event.getIndex() + ")",
                                 "");
            }
        } else {
            writer_.println("/* <No event generators provided for this device.> */");
        }

        writer_.println();

        // Users
        //
        writeHeaderSectionHeading(writer_, "Device Event User Macros");
        
        List<AtdfEvent> userList = device.getEventUsers();

        if(!userList.isEmpty()) {
            for(AtdfEvent event : userList) {
                writeStringMacro(writer_,
                                 "EVENT_ID_USER_" + event.getName(),
                                 "(" + event.getIndex() + ")",
                                 "");
            }
        } else {
            writer_.println("/* <No event uses provided for this device.> */");
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
     * macros defined by Arm's CMSIS headers for this purpose ("__IM " for read-only, "__OM " for
     * write-only, or "__IOM" for both).  This will return "     " (5 spaces) if the register
     * represents a group alias.
     */
    private String getIOMacroFromRegisterAccess(AtdfRegister reg) {
        if(reg.isGroupAlias()) {
            return "     ";
        } else {
            switch(reg.getRwAsInt()) {
                case AtdfRegister.REG_READ:
                    return "__IM ";
                case AtdfRegister.REG_WRITE:
                    return "__OM ";
                default:
                    return "__IOM";
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

    /* Return a string with the starting portion removed if it is present.  This will also remove
     * a separating underscore, so that a 'str' of "START_STR" and 'start' of "START" will return
     * just "STR".
     */
    private String removeStartOfString(String str, String start) {
        if(str.startsWith(start)) {
            str = str.substring(start.length());

            if(str.startsWith("_")) {
                str = str.substring(1);
            }
        }
        
        return str;
    }

    /* Sort the given list of AtdfRegisters by their offset value, with lower offsets coming first.
     */
    private void sortAtdfRegistersByOffset(List<AtdfRegister> registerList) {
        Collections.sort(registerList, new Comparator<AtdfRegister>() {
            @Override
            public int compare(AtdfRegister one, AtdfRegister two) {
                long oneOffset = one.getBaseOffset();
                long twoOffset = two.getBaseOffset();

                if(oneOffset > twoOffset)
                    return 1;
                else if(oneOffset < twoOffset)
                    return -1;
                else
                    return 0;
            }
        });
    }
    
    private void sortPortPinNames(List<String> pinNames) {
        Collections.sort(pinNames, new Comparator<String>() {
            @Override
            public int compare(String one, String two) {
                // For this we can assume that the pin names are all of the form "PA00", "PB01",
                // since that is checked when we get the list from AtdfDoc::getPortPinNames().

                // Compare the port letters "A", "B", etc. first.
                int portDiff = (int)one.charAt(1) - (int)two.charAt(1);

                if(0 == portDiff) {
                    // Same port, so we have to compare the numbers.
                    int pinOne = Integer.parseInt(one.substring(2));
                    int pinTwo = Integer.parseInt(two.substring(2));

                    return pinOne - pinTwo;
                } else {
                    return portDiff;
                }
            }
        });
    }
}
