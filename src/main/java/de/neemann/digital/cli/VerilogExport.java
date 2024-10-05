/*
 * Copyright (c) 2020 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.cli;

import de.neemann.digital.cli.cli.Argument;
import de.neemann.digital.cli.cli.BasicCommand;
import de.neemann.digital.cli.cli.CLIException;
import de.neemann.digital.core.element.Keys;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.gui.Settings;
import de.neemann.digital.hdl.printer.CodePrinter;
import de.neemann.digital.hdl.verilog2.VerilogGenerator;
import de.neemann.digital.lang.Lang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * CLI svg exporter
 */
public class VerilogExport extends BasicCommand {
    private final Argument<String> digFile;
    private final Argument<String> verilogFile;

    /**
     * Creates the SVG export command
     */
    public VerilogExport() {
        super("verilog");

        digFile = addArgument(new Argument<>("dig", "", false));
        verilogFile = addArgument(new Argument<>("verilog", "", false));
    }

    @Override
    protected void execute() throws CLIException {
        try {
            ElementLibrary library = new ElementLibrary(Settings.getInstance().get(Keys.SETTINGS_JAR_PATH));
            Circuit circuit = new CircuitLoader(digFile.get(), true).getCircuit();
            final CodePrinter verilogPrinter = new CodePrinter(Files.newOutputStream(Paths.get(verilogFile.get())));
            try (VerilogGenerator vlog = new VerilogGenerator(library, verilogPrinter)) {
                vlog.export(circuit);
            }
        } catch (IOException e) {
            throw new CLIException(Lang.get("cli_errorCreatingVerilog"), e);
        }
    }
}
