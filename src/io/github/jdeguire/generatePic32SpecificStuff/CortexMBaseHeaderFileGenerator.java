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

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.xml.sax.SAXException;

/**
 *
 * @author jesse
 */
public abstract class CortexMBaseHeaderFileGenerator extends HeaderFileGenerator {
    
    protected final HashSet<String> peripheralFiles_ = new HashSet<>(20);

    public CortexMBaseHeaderFileGenerator(String basepath) {
        super(basepath);
    }

    @Override
    public void generate(TargetDevice target) throws FileNotFoundException, SAXException {
        AtdfDoc atdfDoc = target.getAtdfDocument();

        createNewHeaderFile(target);
        outputLicenseHeader(writer_);
        outputIncludeGuardStart(target);
        outputExternCStart();

        List<AtdfPeripheral> peripherals = atdfDoc.getAllPeripherals();
        List<String> pinNames = atdfDoc.getPortPinNames();
        AtdfDevice device = atdfDoc.getDevice();
        List<AtdfValue> basicDeviceParams = device.getBasicParameterValues();

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
        outputConfigRegisterMacros(writer_, target, ConfigRegMaskType.IMPL_VAL);

        outputExternCEnd();
        outputIncludeGuardEnd(target);
        closeHeaderFile();
    }


    /* Comments for how these are used are in the subclasses. */
    abstract protected void outputLicenseHeader(PrintWriter writer);
    abstract protected void outputCmsisDeclarations(String cpuName);
    abstract protected void outputBaseAddressMacros(List<AtdfPeripheral> peripheralList);
    abstract protected void outputRegisterGroupMacros(PrintWriter writer, AtdfRegisterGroup group,
                                                        int numGroups, long groupOffset,
                                                        AtdfPeripheral peripheral, AtdfInstance instance);
    abstract protected void outputRegisterDefinition(PrintWriter writer,
                                                        AtdfRegister register,
                                                        String groupMode,
                                                        String regNamePrefix) throws SAXException;
    abstract protected void outputGroupDefinition(PrintWriter writer,
                                                    AtdfRegisterGroup group,
                                                    String regNamePrefix);
    abstract protected List<String> getRegisterGroupModes(AtdfRegisterGroup group);


    /* Output the start of the file that includes some typedefs and macro definitions used by the
     * rest of the file and the files it includes.
     */
    protected final void outputPreamble() {
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
    protected final void outputInterruptDefinitions(TargetDevice target) {
        writeHeaderSectionHeading(writer_, "Interrupt Vector Definitions");
        writeNoAssemblyStart(writer_);

        InterruptList interruptList = new InterruptList(target.getPic());
        outputInterruptEnum(interruptList);
        outputInterruptVectorTableTypedef(interruptList);
        outputInterruptHandlerDeclarations(interruptList);

        writeNoAssemblyEnd(writer_);
        writer_.println();
    }

    /* Output a C enum whose values correspond to the interrupt requests on the device.  This will
     * output to the main header file using this object's writer.
     */
    protected final void outputInterruptEnum(InterruptList interruptList) {
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
        writer_.println("  PERIPH_MAX_IRQn                = " + interruptList.getLastVectorNumber() + ",");
        writer_.println("  PERIPH_COUNT_IRQn              = " + (interruptList.getLastVectorNumber() + 1));
        writer_.println("} IRQn_Type;");
        writer_.println();
    }

    /* Output the typedef of the interrupt vector table, which is a struct of void pointers.  Note
     * that this is just the typedef and the table is actually defined in the C startup file for this
     * device.  This will output to the main header file using this object's writer.
     */
    protected final void outputInterruptVectorTableTypedef(InterruptList interruptList) {
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
    protected final void outputInterruptHandlerDeclarations(InterruptList interruptList) {
        for(InterruptList.Interrupt vector : interruptList.getInterruptVectors()) {
            writer_.println("void " + vector.getName() + "_Handler(void);");
        }

        writer_.println();
    }

    protected final void outputBasicCpuParameters(String cpuName, List<AtdfValue> paramList) {
        cpuName = Utils.makeOnlyFirstLetterUpperCase(cpuName);
        writeHeaderSectionHeading(writer_, "Basic config parameters for " + cpuName);

        for(AtdfValue param : paramList) {
            writeStringMacro(writer_, param.getName(), param.getValue(), param.getCaption());
        }

        writer_.println();
    }

    /* Each peripheral has a header file filled with structs and macros describing its member SFRs
     * and layout.  This will output said header files for this device, skipping over ones that have
     * been already output from previous calls, and write the include directives to the main device 
     * file needed to include these files.
     */
    protected final void outputPeripheralDefinitionHeaders(List<AtdfPeripheral> peripheralList) 
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
                    outputLicenseHeader(peripheralWriter);
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

                        for(String mode : getRegisterGroupModes(registerGroup)) {
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

    /* Output a header file that contains information on all of the peripheral instances on the
     * device, such as some basic instance-specific macros and register addresses.
     */
    protected final void outputPeripheralInstancesHeader(TargetDevice target, List<AtdfPeripheral> peripheralList)
            throws FileNotFoundException {
        writeHeaderSectionHeading(writer_, "Device-specific Peripheral Instance Definitions");

        String deviceName = getDeviceNameForHeader(target);
        String instancesMacro = "_" + deviceName.toUpperCase() + "_INSTANCES_";
        String instancesFilename = "instances/" + deviceName + ".h";
        String filepath = basepath_ + "/" + instancesFilename;

        try(final PrintWriter instancesWriter = Utils.createUnixPrintWriter(filepath)) {
            // Output top-of-file stuff like license and include guards.
            outputLicenseHeader(instancesWriter);
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
    protected final void outputPeripheralInstanceWrapperMacroDefinitions(PrintWriter writer) {
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
    protected final void outputPeripheralInstanceRegisterMacros(PrintWriter writer, AtdfPeripheral peripheral, AtdfInstance instance) {
        // The "base group" has the same name as the peripheral.
        AtdfRegisterGroup baseGroup = peripheral.getRegisterGroupByName(peripheral.getName());
        if(null == baseGroup) {
            return;
        }

        writer.print("/* ========== ");
        writer.print("Register definition for " + instance.getName() + " peripheral instance");
        writer.println(" ========== */");
        outputRegisterGroupMacros(writer, baseGroup, 1, 0, peripheral, instance);
    }

    /* Output macros providing peripheral-specific information about the given instance.
     */
    protected final void outputPeripheralInstanceParameterMacros(PrintWriter writer, AtdfInstance instance) {
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
    protected final void outputPeripheralModuleIdMacros(List<AtdfPeripheral> peripheralList) {
        writeHeaderSectionHeading(writer_, "Peripheral ID Macros");

        int maxId = -1000;

        for(AtdfPeripheral peripheral : peripheralList) {
            if(isArmInternalPeripheral(peripheral)) {
                continue;
            }

            try {
                for(AtdfInstance instance : peripheral.getAllInstances()) {
                    int id = instance.getInstanceId();
                    if(id >= 0) {
                        String idStr = "(" + Integer.toString(id) + ")";
                        writeStringMacro(writer_, "ID_" + instance.getName(), idStr, null);

                        if(id > maxId) {
                            maxId = id;
                        }
                    }
                }
            } catch(SAXException ex) {
                // Do nothing for now because some peripherals do not have a publicly-documented
                // instance (crypto peripherals are like this, for example).
            }
        }

        writeStringMacro(writer_, "ID_PERIPH_MAX", "(" + Integer.toString(maxId) + ")", null);
        writeStringMacro(writer_, "ID_PERIPH_COUNT", "(" + Integer.toString(maxId + 1) + ")", null);
        writer_.println();
    }

    /* Output a header file that contains information on all of the peripheral instances on the
     * device, such as some basic instance-specific macros and register addresses.
     */
    protected final void outputPioDefinitionHeader(TargetDevice target, List<String> pinNames, List<AtdfPeripheral> peripheralList) throws FileNotFoundException {
        writeHeaderSectionHeading(writer_, "Device-specific Port IO Definitions");

        String deviceName = getDeviceNameForHeader(target);
        String pioMacro = "_" + deviceName.toUpperCase() + "_PIO_";
        String pioFilename = "pio/" + deviceName + ".h";
        String filepath = basepath_ + "/" + pioFilename;

        try(final PrintWriter pioWriter = Utils.createUnixPrintWriter(filepath)) {
            // Output top-of-file stuff like license and include guards.
            // Use Apache license here because these files are based on the ones that came with XC32
            // v2.30, which were Apache licensed.
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
    protected final void outputPioPortPinMacros(PrintWriter writer, String pin) {
        int portNum = (int) pin.charAt(1) - (int) 'A';
        int pinNum = Integer.parseInt(pin.substring(2));

        writeStringMacro(writer, "PIN_" + pin, "(" + (32 * portNum + pinNum) + ")", "Pin number for " + pin);
        writeStringMacro(writer, "PORT_" + pin, "(_UL_(1) << " + pinNum + ")", "Port mask for " + pin);
    }

    /* Output macros that seem to indicate what device pins can be used by the given peripheral
     * instance and might be used to set up pin muxes.
     */
    protected final void outputPioPeripheralInstanceMacros(PrintWriter writer, AtdfInstance instance) {
        String instName = instance.getName();
        writer.println("/*********** Pio macros for peripheral instance " + instance.getName() + " ***********/");

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
                int portNum = (int) pad.charAt(1) - (int) 'A';
                int pinMux = (int) function.charAt(0) - (int) 'A';

                String macroSuffix = pad + function + "_" + instName + "_" + sigGroup + sigIndex;
                String pinMacro = "PIN_" + macroSuffix;
                String muxMacro = "MUX_" + macroSuffix;
                String caption = instName + " signal: " + sigGroup + " on " + pad + " mux " + function;

                writeStringMacro(writer, pinMacro, "_L_(" + (32 * portNum + pinNum) + ")", caption);
                writeStringMacro(writer, muxMacro, "_L_(" + pinMux + ")", null);
                writeStringMacro(writer, "PINMUX_" + macroSuffix, "((" + pinMacro + " << 16) | " + muxMacro + ")", null);
                writeStringMacro(writer, "PORT_" + macroSuffix, "(_UL_(1) << " + pinNum + ")", null);
                writeStringMacro(writer, "PIO_" + macroSuffix, "(_UL_(1) << " + pinNum + ")", null);
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
    protected final void outputMemoryMapMacros(AtdfDevice device) {
        writeHeaderSectionHeading(writer_, "Memory Segment Macros");

        List<AtdfMemSegment> memSegmentList = device.getMemorySegments();
        for(AtdfMemSegment memSegment : memSegmentList) {
            String name = memSegment.getName();
            long startAddr = memSegment.getStartAddress();
            long pageSize = memSegment.getPageSize();
            long totalSize = memSegment.getTotalSize();

            writeStringMacro(writer_, name + "_ADDR", "_UL_(0x" + Long.toHexString(startAddr) + ")", name + " base address");
            writeStringMacro(writer_, name + "_SIZE", "_UL_(0x" + Long.toHexString(totalSize) + ")", name + " size");

            if(pageSize > 0) {
                writeStringMacro(writer_, name + "_PAGE_SIZE", Long.toString(pageSize), name + " page size");
                writeStringMacro(writer_, name + "_NB_OF_PAGES", Long.toString(totalSize / pageSize), name + " number of pages");
            }

            writer_.println();
        }
    }

    /* Output macros for chip ID info if they are available for the given device.
     */
    protected final void outputDeviceSignatureMacros(AtdfDevice device) {
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
    protected final void outputElectricalParameterMacros(AtdfDevice device) {
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
    protected final void outputEventMacros(AtdfDevice device) {
        // Generators
        //
        writeHeaderSectionHeading(writer_, "Device Event Generator Macros");
 
        List<AtdfEvent> generatorList = device.getEventGenerators();
        if(!generatorList.isEmpty()) {
            for(AtdfEvent event : generatorList) {
                writeStringMacro(writer_, "EVENT_ID_GEN_" + event.getName(), "(" + event.getIndex() + ")", "");
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
                writeStringMacro(writer_, "EVENT_ID_USER_" + event.getName(), "(" + event.getIndex() + ")", "");
            }
        } else {
            writer_.println("/* <No event users provided for this device.> */");
        }

        writer_.println();
    }


    /* Step through the list of bitfields and coalesce related adjacent single-bit fields into
     * larger multi-bit fields.  The Atmel headers called the structs that contained these "vec" and
     * so this generator uses that name; hence, "vecfield".
     */
    protected final List<AtdfBitfield> getVecfieldsFromBitfields(List<AtdfBitfield> bitfieldList) {
        ArrayList<AtdfBitfield> vecfieldList = new ArrayList<>(8);
        VecField vecfield = null;
        int bfNextpos = 0;

        for(AtdfBitfield bitfield : bitfieldList) {
            boolean endCurrentVecfield = true;
            boolean startNewVecfield = false;
            boolean gapWasPresent = bitfield.getLsb() > bfNextpos;

            // Check if we need to start a new vector field or continue one from this bitfield.
            // We need a vector field if this bitfield has a width of 1 and has a number at the end
            // of its name (basename != bitfield name).
            String bfBasename = Utils.getInstanceBasename(bitfield.getName());
            boolean bfHasNumberedName = !bfBasename.equals(bitfield.getName());
            if(bfHasNumberedName && bitfield.getBitWidth() == 1) {
                if(!gapWasPresent && null != vecfield && bfBasename.equals(vecfield.getName())) {
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

            bfNextpos = bitfield.getMsb() + 1;
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
            for(int i = vecfieldList.size() - 2; i >= 0; --i) {
                String iName = vecfieldList.get(i).getName();
                boolean duplicateFound = false;

                for(int j = vecfieldList.size() - 1; j > i; --j) {
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

    /* Return the wrapper macro to use for the given register based on its size and accessibility.
     */
    protected final String getPeripheralInstanceWrapperMacro(AtdfRegister reg) {
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

    /* Return the C99 type to be used with the SFR based on its size.
     */
    protected final String getC99TypeFromRegisterSize(AtdfRegister reg) {
        switch(reg.getSizeInBytes()) {
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
    protected final String getIOMacroFromRegisterAccess(AtdfRegister reg) {
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
    protected final void writePeripheralVersionMacro(PrintWriter writer, AtdfPeripheral peripheral) {
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

    /* We don't want to generate header files and macros for Arm peripherals (like NVIC, ETM, etc.)
     * because those are already handled by the Arm CMSIS headers.  This will return True if the
     * given periphral is an Arm one instead of an Atmel one.
     */
    protected final boolean isArmInternalPeripheral(AtdfPeripheral peripheral) {
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
    protected final boolean isModeNameDefault(String modeName) {
        return null == modeName || modeName.isEmpty() || modeName.equals("DEFAULT");
    }

    /* Write a comment that could be used to call out a new portion of the header file as a section
     * of particular importance on its own, such as a section for interrupt or a memory map.
     */
    protected final void writeHeaderSectionHeading(PrintWriter writer, String heading) {
        writer.println("/******");
        writer.println(" * " + heading);
        writer.println(" */");
    }

    /* Return a string with the starting portion removed if it is present.  This will also remove
     * a separating underscore, so that a 'str' of "START_STR" and 'start' of "START" will return
     * just "STR".
     */
    protected final String removeStartOfString(String str, String start) {
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
    protected final void sortAtdfRegistersByOffset(List<AtdfRegister> registerList) {
        Collections.sort(registerList, new Comparator<AtdfRegister>() {
            @Override
            public int compare(AtdfRegister one, AtdfRegister two) {
                long oneOffset = one.getBaseOffset();
                long twoOffset = two.getBaseOffset();

                if(oneOffset > twoOffset) {
                    return 1;
                } else if(oneOffset < twoOffset) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });
    }

    protected final void sortPortPinNames(List<String> pinNames) {
        Collections.sort(pinNames, new Comparator<String>() {
            @Override
            public int compare(String one, String two) {
                // For this we can assume that the pin names are all of the form "PA00", "PB01",
                // since that is checked when we get the list from AtdfDoc::getPortPinNames().
                // Compare the port letters "A", "B", etc. first.
                int portDiff = (int) one.charAt(1) - (int) two.charAt(1);

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
