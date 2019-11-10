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

    /* This is here for convenience when we create custom fields that are "vectors" of adjacent fields
     */
    private class BlankBitfield extends AtdfBitfield {
        public String name_;
        public String owner_;
        public String caption_;
        public long mask_;

        BlankBitfield(String name, String owner, String caption, long mask) {
            super(null, null, null);
            name_ = name;
            owner_ = owner;
            caption_ = caption;
            mask_ = mask;
        }

        BlankBitfield() {
            this("", "", "", 0);
        }

        @Override
        public String getName() { return name_; }
        
        @Override
        public String getOwningRegisterName() { return owner_; }

        @Override
        public String getCaption() { return caption_; }
        
        @Override
        public long getMask() { return mask_; }
        
        @Override
        public List<AtdfValue> getFieldValues() { return Collections.<AtdfValue>emptyList(); }
    }

    private final HashSet<String> peripheralFiles_ = new HashSet<>(20);


    public CortexMHeaderFileBuilder(String basepath) {
        super(basepath);
    }

    @Override
    public void generate(TargetDevice target) throws java.io.FileNotFoundException {
        String basename = target.getDeviceName();
        AtdfDoc atdfDoc;
        try {
            atdfDoc = new AtdfDoc(basename);
        } catch(Exception ex) {
            throw new java.io.FileNotFoundException(ex.getMessage());
        }

        createNewHeaderFile(target);

        List<AtdfPeripheral> peripherals = atdfDoc.getAllPeripherals();

        outputLicenseHeader(writer_, true);
        outputPeripheralDefinitionHeaders(peripherals);

        closeHeaderFile();
    }


    /* Each peripheral has a header file filled with structs and macros describing its member SFRs
     * and layout.  This will output said header files for this device, skipping over ones that have
     * been already output from previous calls, and write the include directives to the main device 
     * file needed to include these files.
     */
    private void outputPeripheralDefinitionHeaders(List<AtdfPeripheral> peripheralList) 
                                    throws java.io.FileNotFoundException {
        for(AtdfPeripheral peripheral : peripheralList) {
            String peripheralName = peripheral.getName();
            String peripheralId = peripheral.getModuleId();
            String peripheralMacro = (peripheralName + "_" + peripheralId).toUpperCase();
            String peripheralFilename = "/component/" + peripheralMacro.toLowerCase() + ".h";

            // ARM peripherals (like NVIC, ETM, etc.) do not have IDs, so use this to figure out
            // which peripherals are ARM ones and do not add those.
            if(peripheralId.isEmpty()) {
                continue;
            }

            // Did we already generate a file for this peripheral?
            if(!peripheralFiles_.contains(peripheralFilename)) {
                // Nope, so time to create one.
                String filepath = basepath_ + peripheralFilename;

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
                        for(AtdfRegister register : registerGroup.getAllMembers()) {
                            if(!register.isGroupAlias())
                                outputRegisterDefinition(peripheralWriter, register);
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
    }

    /* Output a C struct representing the layout of the given register and a bunch of C macros that 
     * can be used to access the fields within it.  This will also output some descriptive text as
     * given in the ATDF document associated with this device.
     */
    private void outputRegisterDefinition(PrintWriter writer, AtdfRegister register) {
        ArrayList<String> bits = new ArrayList<>(32);
        ArrayList<String> vecs = new ArrayList<>(32);
        ArrayList<String> macros = new ArrayList<>(32);

        String peripheralName = register.getOwningPeripheralName();
        String fullRegisterName = peripheralName + "_" + register.getName();
        String c99type = getC99TypeFromRegisterSize(register);
        BlankBitfield vecfield = null;

        int bfNextpos = 0;
        int bfGap;
        int vecNextpos = 0;
        int vecGap;
        boolean hasVecs = false;
        for(AtdfBitfield bitfield : register.getAllBitfields()) {
            boolean endCurrentVecfield = true;
            boolean startNewVecfield = false;

            // Do we have a gap we need to fill in our bitfield?
            bfGap = bitfield.getLsb() - bfNextpos;
            if(bfGap > 0) {
                // End the current vecfield if there's a gap.
                bits.add("    " + c99type + "  :" + bfGap + ";");
            }

            // Check if we need to start a new vector field or continue a vector from this bitfield.
            // We need a vector field if this bitfield has a width of 1 and has a number at the end
            // of its name (basename != bitfield name).
            String bfBasename = Utils.getInstanceBasename(bitfield.getName());
            boolean bfHasNumberedName = !bfBasename.equals(bitfield.getName());
            if(bfHasNumberedName  &&  bitfield.getBitWidth() == 1) {
                if(0 == bfGap  &&  null != vecfield  &&  bfBasename.equals(vecfield.getName())) {
                    // Continue a current vector.
                    endCurrentVecfield = false;
                    vecfield.mask_ |= bitfield.getMask();
                } else {
                    // Start a new vector and output the old one.
                    startNewVecfield = true;
                }
            }

            // Do we need to write out our current vector field and possible create a new one?
            if(endCurrentVecfield) {
                if(null != vecfield) {
                    vecGap = vecfield.getLsb() - vecNextpos;
                    if(vecGap > 0)
                        vecs.add("    " + c99type + "  :" + vecGap + ";");

                    vecs.add(makeBitfieldDeclaration(vecfield, c99type));
                    macros.addAll(makeBitfieldMacros(vecfield, peripheralName, true));
                    vecNextpos = vecfield.getMsb()+1;
                }
                
                if(startNewVecfield) {
                    vecfield = new BlankBitfield(bfBasename,
                                                 bitfield.getOwningRegisterName(),
                                                 bitfield.getCaption().replaceAll("\\d", "x"),
                                                 bitfield.getMask());
                    hasVecs = true;
                } else {
                    vecfield = null;
                }
            }

            // Output bitfield info.
            bits.add(makeBitfieldDeclaration(bitfield, c99type));
            macros.addAll(makeBitfieldMacros(bitfield, peripheralName, bitfield.getBitWidth() > 1));
            bfNextpos = bitfield.getMsb()+1;

            // Some bitfields use C macros to indicate what the different values of the bitfield
            // mean.  If this has those, then generate them here.
            try {
                List<AtdfValue> fieldValues = bitfield.getFieldValues();
                if(!fieldValues.isEmpty()) {
                    String valueMacroBasename = fullRegisterName + "_" + bitfield.getName() + "_";

                    // Create first set of macros containing option values.
                    for(AtdfValue val : fieldValues) {
                        String valueMacroName = valueMacroBasename + val.getName() + "_Val";
                        String valueMacroValue = "_U_(" + val.getValue() + ")";
                        String valueMacroCaption = val.getCaption();
                        macros.add(makeStringMacro("  " + valueMacroName, valueMacroValue, valueMacroCaption));
                    }

                    // Now create second set which uses first set.
                    for(AtdfValue val : fieldValues) {
                        String optMacroName = valueMacroBasename + val.getName();
                        String valMacroName = optMacroName + "_Val";
                        String posMacroName = valueMacroBasename + "Pos";
                        macros.add(makeStringMacro(optMacroName, "(" + valMacroName + " << " + posMacroName + ")", ""));
                    }
                }
            } catch(SAXException e) {
                // Couldn't read values; do nothing for now,
            }
        }

        // Add last vector field if one is present.
        if(null != vecfield) {
            vecGap = vecfield.getLsb() - vecNextpos;
            if(vecGap > 0)
                vecs.add("    " + c99type + "  :" + vecGap + ";");

            vecs.add(makeBitfieldDeclaration(vecfield, c99type));
            macros.addAll(makeBitfieldMacros(vecfield, peripheralName, true));
            vecNextpos = vecfield.getMsb()+1;
        }

        int bitwidth = register.getSizeInBytes() * 8;

        // Fill unused bits at end if needed.
        bfGap = bitwidth - bfNextpos;
        if(bfGap > 0) {
            bits.add("    " + c99type + "  :" + bfGap + ";");
        }

        vecGap = bitwidth - vecNextpos;
        if(vecGap > 0) {
            vecs.add("    " + c99type + "  :" + vecGap + ";");
        }


        // Now it's time to start writing stuff out.
        //
        String captionStr = ("/* -------- " + fullRegisterName + " : (" + peripheralName + " Offset: ");
        String offsetStr = String.format("0x%02X", register.getBaseOffset());
        captionStr += offsetStr;
        captionStr += ") (" + register.getRwAsString() + " " + bitwidth + ") " + register.getCaption();
        captionStr += " -------- */";

        writer.println(captionStr);
        writeNoAssemblyStart(writer);

        writer.println("typedef union {");
        writer.println("  struct {");
        for(String bitstr : bits) {
            writer.println(bitstr);
        }
        writer.println("  } bit;");
        if(hasVecs) {
            writer.println("  struct {");
            for(String vecstr : vecs) {
                writer.println(vecstr);
            }
            writer.println("  } vec;");
        }
        writer.println("  " + c99type + " reg;");
        writer.println("} " + register.getTypeName());

        writeNoAssemblyEnd(writer);
        writer.println();

        writeStringMacro(writer, 
                         fullRegisterName + "_OFFSET",
                         "(" + offsetStr + ")",
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
        for(String macro : macros) {
            writer.println(macro);
        }
        writer.println();
    }

    /* Output a C struct representing the layout of the given register group.
     */
    private void outputGroupDefinition(PrintWriter writer, AtdfRegisterGroup group) {
        long regNextOffset = 0;
        long regGapNumber = 1;

        writeNoAssemblyStart(writer);
        writer.println("typedef struct {");

        for(AtdfRegister reg : group.getAllMembers()) {
            long regGap = reg.getBaseOffset() - regNextOffset;

            if(regGap > 0) {
                String gapStr = "       RoReg8";
                gapStr = Utils.padStringWithSpaces(gapStr, 32, 4);
                gapStr += "Reserved" + regGapNumber + "[" + regGap + "];";

                writer.println(gapStr);
                ++regGapNumber;
                regNextOffset = reg.getBaseOffset();
            }

            String regStr = "  " + getIOMacroFromRegisterAccess(reg) + " " + reg.getTypeName();
            regStr = Utils.padStringWithSpaces(regStr, 32, 4);

            regStr += reg.getName();
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

        int alignment = group.getAlignment();
        if(alignment > 0) {
            writer.println("} " + group.getTypeName());
            writer.println("#ifdef __GNUC__");
            writer.println("  __attribute__((aligned(" + alignment + ")))");
            writer.println("#endif");
            writer.println(";");
        } else {
            writer.println("} " + group.getTypeName() + ";");
        }

        writeNoAssemblyEnd(writer);
        writer.println();

        String section = group.getMemorySection();
        if(!section.isEmpty()) {
            writer.println("#ifdef __GNUC__");
            writer.println("#  define SECTION_DMAC_DESCRIPTOR    __attribute__ ((section(\"." + section + "\")))");
            writer.println("#elif defined(__ICCARM__)");
            writer.println("#  define SECTION_DMAC_DESCRIPTOR    @\"." + section + "\"");
            writer.println("#endif");
            writer.println();
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
     * write-only, or "__IO" for both).
     */
    private String getIOMacroFromRegisterAccess(AtdfRegister reg) {
        int rw = reg.getRwAsInt();

        switch(rw) {
            case AtdfRegister.REG_READ:
                return "__I ";
            case AtdfRegister.REG_WRITE:
                return "__O ";
            default:
                return "__IO";
        }
    }

    /* These next four are just convenience methods used to output idefs and endifs that block out
     * sections of header file based on wether or not an assembler is running.
     */
    private void writeNoAssemblyStart(PrintWriter writer) {
        writer.println("#if !(defined(__ASSEMBLY__) || defined(__IAR_SYSTEMS_ASM__))");
    }

    private void writeNoAssemblyEnd(PrintWriter writer) {
        writer.println("#endif /* !(defined(__ASSEMBLY__) || defined(__IAR_SYSTEMS_ASM__)) */");
    }

    private void writeAssemblyStart(PrintWriter writer) {
        writer.println("#if (defined(__ASSEMBLY__) || defined(__IAR_SYSTEMS_ASM__))");
    }

    private void writeAssemblyEnd(PrintWriter writer) {
        writer.println("#endif /* (defined(__ASSEMBLY__) || defined(__IAR_SYSTEMS_ASM__)) */");
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

    /* Write a macro for the peripheral's vesrion number as taken from the given ATDF document.
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

    /* Make a bitfield declaration that would be used as part of a C struct.
     */
    private String makeBitfieldDeclaration(AtdfBitfield bitfield, String c99type) {
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

        return fieldDecl;
    }

    /* Create and return a list of C macros that are used to access the given bitfield.  This can 
     * generate two sets of macros depending on the state of 'extendedMacros'.  The normal set
     * contains just a position macro and a mask macro.  Extended macros also contain an additional
     * function-like macro to set the value.
     */
    private List<String> makeBitfieldMacros(AtdfBitfield bitfield, String peripheralName, boolean extendedMacros) {
        ArrayList<String> macroList = new ArrayList<>(3);
        String fullRegisterName = bitfield.getOwningRegisterName();

        if(!fullRegisterName.startsWith(peripheralName))
            fullRegisterName = peripheralName + "_" + fullRegisterName;
 
        String qualifiedName = fullRegisterName + "<" + bitfield.getName() + ">";
        String baseMacroName = fullRegisterName + "_" + bitfield.getName();
        String posMacroName = baseMacroName + "_Pos";

        macroList.add(makeStringMacro(posMacroName,
                                      "(" + bitfield.getLsb() + ")",
                                      qualifiedName + ": " + bitfield.getCaption()));

        if(extendedMacros) {
            String maskMacroName = baseMacroName + "_Msk";
            String valueMacroName = baseMacroName + "(value)";

            macroList.add(makeStringMacro(maskMacroName,
                                          String.format("_U_(0x%X)", bitfield.getMask()),
                                          ""));
            macroList.add(makeStringMacro(valueMacroName,
                                          String.format("(%s & ((value) << %s))", maskMacroName, posMacroName),
                                          ""));
        } else {
            macroList.add(makeStringMacro(baseMacroName,
                                          String.format("_U_(0x%X)", bitfield.getMask()),
                                          ""));
        }

        return macroList;
    }
}
