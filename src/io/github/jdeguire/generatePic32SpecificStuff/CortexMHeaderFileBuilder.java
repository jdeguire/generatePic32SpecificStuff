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
import com.microchip.crownking.edc.SFR;
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
//            ArrayList<String> macros = new ArrayList<>(32);

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

            String sfrName = Utils.getInstanceBasename(sfr.getName());
            String peripheralBasename = Utils.getInstanceBasename(sfr.getPeripheralIDs().get(0));
            AtdfDoc.Register atdfRegister = atdfDoc.getPeripheralRegister(peripheralBasename, sfrName);

            if(!sfrName.startsWith(peripheralBasename))
                sfrName = peripheralBasename + "_" + sfrName;

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
            captionStr += String.format("0x%02X", atdfRegister.getBaseOffset());
            captionStr += ") (" + rwStr + " " + sfr.getWidth()+ ") " + atdfRegister.getCaption();
            captionStr += " -------- */";

            writer_.println(captionStr);
            outputNoAssemblyStart();

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

            outputNoAssemblyEnd();
            writer_.println();
        }
    }

    private void outputNoAssemblyStart() {
        writer_.println("#if !(defined(__ASSEMBLY__) || defined(__IAR_SYSTEMS_ASM__))");
    }

    private void outputNoAssemblyEnd() {
        writer_.println("#endif /* !(defined(__ASSEMBLY__) || defined(__IAR_SYSTEMS_ASM__)) */");
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
}
