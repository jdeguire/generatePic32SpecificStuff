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

import com.microchip.crownking.edc.Bitfield;
import com.microchip.crownking.edc.Option;
import com.microchip.crownking.edc.SFR;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Node;

/**
 * A subclass of the HeaderFileBuilder that handles ARM Cortex-M devices.
 */
public class CortexMHeaderFileBuilder extends HeaderFileBuilder {
    
    private final LinkedHashMap<String, ArrayList<SFR>> peripheralSFRs_ = new LinkedHashMap<>(20);

    CortexMHeaderFileBuilder(String basepath) {
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

        peripheralSFRs_.clear();
        populateSFRs(target);

        outputLicenseHeader(true);

        Map.Entry<String, ArrayList<SFR>> entry = peripheralSFRs_.entrySet().iterator().next();

        String peripheralName = entry.getKey();
        String peripheralId = atdfDoc.getPeripheralModuleId(peripheralName);
        String peripheralMacro = peripheralName + "_" + peripheralId;
        writer_.println();
        writeStringMacro(writer_, peripheralMacro.toUpperCase(), "", "");
        writePeripheralVersionMacro(writer_, peripheralName, atdfDoc);
        writer_.println();

        String previousBasename = "";
        for(SFR sfr : entry.getValue()) {
            String sfrBasename = Utils.getInstanceBasename(sfr.getName());
            if(!previousBasename.equals(sfrBasename)) {
                previousBasename = sfrBasename;
                outputSFRDefinition(sfr, atdfDoc);
            }
        }

        closeHeaderFile();
    }

    private void populateSFRs(TargetDevice target) {
// TODO:  Do we even need this first part?  Could we just add peripherals as they appear?
        // The "edc:" prefix is not needed because we're going through the 'xPIC' object.
        Node peripheralListNode = target.getPic().first("PeripheralList");

        // The "edc:" prefix is needed here because we're going through the Node object.
        List<Node> peripheralNodes = Utils.filterAllChildNodes(peripheralListNode, "edc:Peripheral", 
                                                               "edc:cname", null);

        // Initialize the map with the listed peripherals.
        for(Node node : peripheralNodes) {
            String peripheralName = Utils.getNodeAttribute(node, "edc:cname", null);
            if(null != peripheralName)
                peripheralSFRs_.put(peripheralName, new ArrayList<SFR>(10));
        }

        // Now, get all of the SFRs and put them with their member peripherals.
        List<SFR> allSFRs = target.getSFRs();
        for(SFR sfr : allSFRs) {
            // Anything above this range is reserved for system functions and the ARM private 
            // peripheral bus (NVIC, SysTic, etc.), which we can ignore.
            if(target.getRegisterAddress(sfr) < 0xE0000000L) {
                // This gets the member peripherals from the "ltx:memberofperipheral" XML attribute.
                List<String> peripheralIDs = sfr.getPeripheralIDs();

                if(!peripheralIDs.isEmpty()) {
                    String peripheralId = peripheralIDs.get(0);
                    ArrayList<SFR> peripheralList = peripheralSFRs_.get(peripheralId);

// TODO:  Could we just add peripherals as they appear (ie. when this is null)?
                    if(null != peripheralList) {
                        peripheralList.add(sfr);
                    }
                }
            }
        }
    }

    private void outputSFRDefinition(SFR sfr, AtdfDoc atdfDoc) {
        if(null != sfr) {
            ArrayList<String> bits = new ArrayList<>(32);
            ArrayList<String> vecs = new ArrayList<>(32);
            ArrayList<String> macros = new ArrayList<>(32);

            String sfrName = Utils.getInstanceBasename(sfr.getName());
            String peripheralBasename = Utils.getInstanceBasename(sfr.getPeripheralIDs().get(0));
            AtdfDoc.Register atdfRegister = atdfDoc.getPeripheralRegister(peripheralBasename, sfrName);

            if(!sfrName.startsWith(peripheralBasename))
                sfrName = peripheralBasename + "_" + sfrName;            

            String type = getTypeFromSfrWidth(sfr);
            int bfNextpos = 0;
            int bfGap;
            String vecName = "";
            String vecDesc = "";
            int vecPlace = 0;
            int vecWidth = 0;
            int vecNextpos = 0;
            int vecGap;
            boolean hasVecs = false;
            for(Bitfield bf : sfr.getBitfields()) {
                String bfName = bf.getName();
                String bfDesc = bf.getDesc();
                int bfPlace = bf.getPlace().intValue();
                int bfWidth = bf.getWidth().intValue();

                // Do we have a gap we need to fill in our bitfield?
                bfGap = bfPlace - bfNextpos;
                if(bfGap > 0) {
                    if(!vecName.isEmpty()) {
                        vecGap = vecPlace - vecNextpos;
                        if(vecGap > 0)
                            vecs.add("    " + type + "  :" + vecGap + ";");

                        String vecstr = "    " + type + "  " + vecName + ":" + vecWidth + ";";
                        while(vecstr.length() < 36)
                            vecstr += ' ';

                        if(vecWidth > 1) {
                            vecstr += String.format("/* bit: %2d..%2d  %s */", vecPlace, vecPlace+vecWidth-1, vecDesc);
                        } else {
                            vecstr += String.format("/* bit:     %2d  %s */", vecPlace, vecDesc);
                        }

                        String macroBasename = sfrName + "_" + vecName;
                        String posMacroName = macroBasename + "_Pos";
                        String maskMacroName = macroBasename + "_Msk";
                        String valueMacroName = macroBasename + "(value)";
                        macros.add(makeStringMacro(posMacroName, "(" + Integer.toString(vecPlace) + ")", sfrName + ": " + vecDesc));
                        macros.add(makeStringMacro(maskMacroName, String.format("(_U_(0x%X) << %s)", (1L << vecWidth)-1, posMacroName), ""));
                        macros.add(makeStringMacro(valueMacroName, String.format("(%s & ((value) << %s))", maskMacroName, posMacroName), ""));

                        vecs.add(vecstr); 
                        vecName = "";
                        vecNextpos = vecPlace + vecWidth;
                   }

                    bits.add("    " + type + "  :" + bfGap + ";");
                }

                // Check if we need to start a new vector or continue a vector from this bitfield.
                String bfBasename = Utils.getInstanceBasename(bfName);
                if(!bfBasename.equals(bfName)  &&  bfWidth == 1) {
                    if(bfBasename.equals(vecName)) {
                        // Continue a current vector.
                        ++vecWidth;
                    } else {
                        // Start a new vector and output the old one.
                        if(!vecName.isEmpty()) {
                            vecGap = vecPlace - vecNextpos;
                            if(vecGap > 0)
                                vecs.add("    " + type + "  :" + vecGap + ";");

                            String vecstr = "    " + type + "  " + vecName + ":" + vecWidth + ";";
                            while(vecstr.length() < 36)
                                vecstr += ' ';

                            if(vecWidth > 1) {
                                vecstr += String.format("/* bit: %2d..%2d  %s */", vecPlace, vecPlace+vecWidth-1, vecDesc);
                            } else {
                                vecstr += String.format("/* bit:     %2d  %s */", vecPlace, vecDesc);
                            }

                            String macroBasename = sfrName + "_" + vecName;
                            String posMacroName = macroBasename + "_Pos";
                            String maskMacroName = macroBasename + "_Msk";
                            String valueMacroName = macroBasename + "(value)";
                            macros.add(makeStringMacro(posMacroName, "(" + Integer.toString(vecPlace) + ")", sfrName + ": " + vecDesc));
                            macros.add(makeStringMacro(maskMacroName, String.format("(_U_(0x%X) << %s)", (1L << vecWidth)-1, posMacroName), ""));
                            macros.add(makeStringMacro(valueMacroName, String.format("(%s & ((value) << %s))", maskMacroName, posMacroName), ""));

                            vecs.add(vecstr);
                            vecName = "";
                            vecNextpos = vecPlace + vecWidth;
                        }

                        vecName = bfBasename;
                        vecDesc = bfDesc.replaceAll("\\d", "x");
                        vecPlace = bfPlace;
                        vecWidth = 1;
                        hasVecs = true;
                    }
                } else if(!vecName.isEmpty()) {
                    // Not part of a vector, so any existing one has ended.
                    vecGap = vecPlace - vecNextpos;
                    if(vecGap > 0)
                        vecs.add("    " + type + "  :" + vecGap + ";");

                    String vecstr = "    " + type + "  " + vecName + ":" + vecWidth + ";";
                    while(vecstr.length() < 36)
                        vecstr += ' ';

                    if(vecWidth > 1) {
                        vecstr += String.format("/* bit: %2d..%2d  %s */", vecPlace, vecPlace+vecWidth-1, vecDesc);
                    } else {
                        vecstr += String.format("/* bit:     %2d  %s */", vecPlace, vecDesc);
                    }

                    String macroBasename = sfrName + "_" + vecName;
                    String posMacroName = macroBasename + "_Pos";
                    String maskMacroName = macroBasename + "_Msk";
                    String valueMacroName = macroBasename + "(value)";
                    macros.add(makeStringMacro(posMacroName, "(" + Integer.toString(vecPlace) + ")", sfrName + ": " + vecDesc));
                    macros.add(makeStringMacro(maskMacroName, String.format("(_U_(0x%X) << %s)", (1L << vecWidth)-1, posMacroName), ""));
                    macros.add(makeStringMacro(valueMacroName, String.format("(%s & ((value) << %s))", maskMacroName, posMacroName), ""));

                    vecs.add(vecstr);
                    vecName = "";
                    vecNextpos = vecPlace + vecWidth;
                }


                String bitstr = "    " + type + "  " + bfName + ":" + bfWidth + ";";
                while(bitstr.length() < 36)
                    bitstr += ' ';

                if(bfWidth > 1) {
                    bitstr += String.format("/* bit: %2d..%2d  %s */", bfPlace, bfPlace+bfWidth-1, bfDesc);
                } else {
                    bitstr += String.format("/* bit:     %2d  %s */", bfPlace, bfDesc);                    
                }

                if(bfWidth > 1) {
                    String macroBasename = sfrName + "_" + bfName;
                    String posMacroName = macroBasename + "_Pos";
                    String maskMacroName = macroBasename + "_Msk";
                    String valueMacroName = macroBasename + "(value)";
                    macros.add(makeStringMacro(posMacroName, "(" + Integer.toString(bfPlace) + ")", sfrName + ": " + bfDesc));
                    macros.add(makeStringMacro(maskMacroName, String.format("(_U_(0x%X) << %s)", (1L << bfWidth)-1, posMacroName), ""));
                    macros.add(makeStringMacro(valueMacroName, String.format("(%s & ((value) << %s))", maskMacroName, posMacroName), ""));                    
                } else {
                    String macroName = sfrName + "_" + bfName;
                    String posMacroName = macroName + "_Pos";
                    macros.add(makeStringMacro(posMacroName, "(" + Integer.toString(bfPlace) + ")", sfrName + ": " + bfDesc));
                    macros.add(makeStringMacro(macroName, "(_U_(1) << " + posMacroName + ")", ""));
                }

                if(bf.hasOptions()) {
                    List<Option> options = bf.getOptions();
                    String optMacroBasename = sfrName + "_" + bfName + "_";

                    // Create first set of macros containing option values.
                    for(Option opt : options) {
                        String valMacroName = optMacroBasename + opt.getName() + "_Val";
                        macros.add(makeStringMacro("  " + valMacroName, String.format("_U_(0x%X)", opt.getValue()), opt.getDesc()));
                    }

                    // Now create second set which uses first set.
                    for(Option opt : options) {
                        String optMacroName = optMacroBasename + opt.getName();
                        String valMacroName = optMacroBasename + opt.getName() + "_Val";
                        String posMacroName = optMacroBasename + opt.getName() + "_Pos";
                        macros.add(makeStringMacro(optMacroName, "(" + valMacroName + " << " + posMacroName + ")", ""));
                    }
                }

                bits.add(bitstr);
                bfNextpos = bfPlace + bfWidth;
            }

            // Add last vector field if one is present.
            if(!vecName.isEmpty()) {
                vecGap = vecPlace - vecNextpos;
                if(vecGap > 0)
                    vecs.add("    " + type + "  :" + vecGap + ";");

                String vecstr = "    " + type + "  " + vecName + ":" + vecWidth + ";";
                while(vecstr.length() < 36)
                    vecstr += ' ';

                if(vecWidth > 1) {
                    vecstr += String.format("/* bit: %2d..%2d  %s */", vecPlace, vecPlace+vecWidth-1, vecDesc);
                } else {
                    vecstr += String.format("/* bit:     %2d  %s */", vecPlace, vecDesc);
                }

                String macroBasename = sfrName + "_" + vecName;
                String posMacroName = macroBasename + "_Pos";
                String maskMacroName = macroBasename + "_Msk";
                String valueMacroName = macroBasename + "(value)";
                macros.add(makeStringMacro(posMacroName, "(" + Integer.toString(vecPlace) + ")", sfrName + ": " + vecDesc));
                macros.add(makeStringMacro(maskMacroName, String.format("(_U_(0x%X) << %s)", (1L << vecWidth)-1, posMacroName), ""));
                macros.add(makeStringMacro(valueMacroName, String.format("(%s & ((value) << %s))", maskMacroName, posMacroName), ""));

                vecs.add(vecstr);
                vecNextpos = vecPlace + vecWidth;
            }

            // Fill unused bits at end if needed.
            bfGap = (int)sfr.getWidth() - bfNextpos;
            if(bfGap > 0) {
                bits.add("    " + type + "  :" + bfGap + ";");
            }

            vecGap = (int)sfr.getWidth() - vecNextpos;
            if(vecGap > 0) {
                vecs.add("    " + type + "  :" + vecGap + ";");
            }

            String rwStr;
            switch(atdfRegister.getRw()) {
                case AtdfDoc.Register.REG_RW:
                    rwStr = "R/W";
                    break;
                case AtdfDoc.Register.REG_READ:
                    rwStr = "R";
                    break;
                default:
                    rwStr = "W";
                    break;
            }

            String captionStr = ("/* -------- " + sfrName + " : (" + peripheralBasename + " Offset: ");
            String offsetStr = String.format("0x%02X", atdfRegister.getBaseOffset());
            captionStr += offsetStr;
            captionStr += ") (" + rwStr + " " + sfr.getWidth()+ ") " + atdfRegister.getCaption();
            captionStr += " -------- */";

            writer_.println(captionStr);
            writeNoAssemblyStart(writer_);

            writer_.println("typedef union {");
            writer_.println("  struct {");
            for(String bitstr : bits) {
                writer_.println(bitstr);
            }
            writer_.println("  } bit;");
            if(hasVecs) {
                writer_.println("  struct {");
                for(String vecstr : vecs) {
                    writer_.println(vecstr);
                }
                writer_.println("  } vec;");
            }
            writer_.println("  " + type + " reg;");
            writer_.println("} " + sfrName + "_Type;");

            writeNoAssemblyEnd(writer_);
            writer_.println();

            writeStringMacro(writer_, sfrName + "_OFFSET", "(" + offsetStr + ")", sfrName + " offset: " + atdfRegister.getCaption());
            writeStringMacro(writer_, sfrName + "_RESETVALUE", String.format("_U_(0x%X)", atdfRegister.getInitValue()), sfrName + " reset value: " + atdfRegister.getCaption());
            writeStringMacro(writer_, sfrName + "_MASK", String.format("_U_(0x%X)", sfr.getImpl()), sfrName + " mask");

            writer_.println();
            for(String macro : macros) {
                writer_.println(macro);
            }
            writer_.println();
        }
    }

    private String getTypeFromSfrWidth(SFR sfr) {
        int regSize = (int)sfr.getWidth();

        if(regSize <= 8) {
            return "uint8_t";
        } else if(regSize <= 16) {
            return "uint16_t";
        } else {
            return "uint32_t";
        }
    }

    private void writeNoAssemblyStart(PrintWriter writer) {
        writer.println("#if !(defined(__ASSEMBLY__) || defined(__IAR_SYSTEMS_ASM__))");
    }

    private void writeNoAssemblyEnd(PrintWriter writer) {
        writer.println("#endif /* !(defined(__ASSEMBLY__) || defined(__IAR_SYSTEMS_ASM__)) */");
    }

    private String makeStringMacro(String name, String value, String desc) {
        String macro = "#define " + name;

        if(null != value  &&  !value.isEmpty()) {
            do {
                macro += " ";
            } while(macro.length() < 36);
            
            macro += value;

            if(null != desc  &&  !desc.isEmpty()) {
                do {
                    macro += " ";
                } while(macro.length() < 50);

                macro += "/* " + desc + " */";
            }
        }

        return macro;
    }

    private void writeStringMacro(PrintWriter writer, String name, String value, String desc) {
        writer.println(makeStringMacro(name, value, desc));
    }

    private void writePeripheralVersionMacro(PrintWriter writer, String peripheral, AtdfDoc atdfDoc) {
        String version = atdfDoc.getPeripheralModuleVersion(peripheral);

        if(null == version) {
            return;
        }

        String macroName = "REV_" + peripheral;

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
}
