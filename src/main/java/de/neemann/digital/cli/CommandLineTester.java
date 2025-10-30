/*
 * Copyright (c) 2019 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.cli;

import de.neemann.digital.cli.cli.Argument;
import de.neemann.digital.cli.cli.BasicCommand;
import de.neemann.digital.cli.cli.CLIException;
import de.neemann.digital.core.ErrorDetector;
import de.neemann.digital.core.IntFormat;
import de.neemann.digital.data.Value;
import de.neemann.digital.data.ValueTable;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.lang.Lang;
import de.neemann.digital.testing.MatchedValue;
import de.neemann.digital.testing.TestExecutor;
import de.neemann.digital.testing.TestResult;
import de.neemann.digital.testing.parser.TestRow;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Tester used from the command line
 */
public class CommandLineTester {

    private final CircuitLoader circuitLoader;
    private final String circuitFilePath;
    private String testCasesFilePath;
    private List<Circuit.TestCase> testCases;
    private int testsPassed;
    private boolean allowMissingInputs;
    private boolean verbose;

    /**
     * Creates a new instance.
     *
     * @param file the file to test
     * @throws IOException IOException
     */
    public CommandLineTester(File file) throws IOException {
        circuitLoader = new CircuitLoader(file);
        this.circuitFilePath = file.getAbsolutePath();
    }

    /**
     * Uses the test cases from the given file
     *
     * @param file the file containing the test cases
     * @return this for chained calls
     * @throws IOException IOException
     */
    public CommandLineTester useTestCasesFrom(File file) throws IOException {
        this.testCasesFilePath = file.getAbsolutePath();
        Circuit c = Circuit.loadCircuit(file, circuitLoader.getShapeFactory());
        testCases = c.getTestCases();
        return this;
    }

    /**
     * Executes test test
     *
     * @param out Stream to output messages
     * @return the number of failed test cases
     */
    public int execute(PrintStream out) {
        if (testCases == null) {
            testCases = circuitLoader.getCircuit().getTestCases();
            testCasesFilePath = circuitFilePath; // If not specified, tests are in the circuit file
        }

        int errorCount = 0;

        if (testCases.isEmpty()) {
            out.println("no test cases given");
            errorCount++;
        } else {
            JSONArray results = new JSONArray();
            for (Circuit.TestCase t : testCases) {
                String label = t.getLabel();
                if (label.isEmpty())
                    label = "unnamed";

                long startTime = System.currentTimeMillis();
                TestResult tr = null;
                String errorMessage = "";
                try {
                    ErrorDetector errorDetector = new ErrorDetector();
                    tr = new TestExecutor(t, circuitLoader.getCircuit(), circuitLoader.getLibrary())
                            .setAllowMissingInputs(allowMissingInputs)
                            .addObserver(errorDetector)
                            .execute();
                    errorDetector.check();
                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    errorCount++;
                }
                long elapsedTime = System.currentTimeMillis() - startTime;

                JSONObject result = new JSONObject();
                result.put("testName", label);
                result.put("fileName", circuitFilePath);
                result.put("testFileName", testCasesFilePath);
                result.put("elapsedTimeMs", elapsedTime);

                if (tr != null) {
                    if (tr.allPassed()) {
                        result.put("status", "PASSED");
                        testsPassed++;
                    } else {
                        result.put("status", "FAILED");
                        errorCount++;
                    }

                    if (tr.isErrorOccurred())
                        errorMessage = "Test failed due to an error";
                } else {
                    result.put("status", "FAILED");
                }


                result.put("errorMessage", errorMessage);

                JSONArray timesteps = new JSONArray();
                if (tr != null) {
                    ValueTable valueTable = tr.getValueTable();

                    JSONArray signalNames = new JSONArray();
                    for (int i = 0; i < valueTable.getColumns(); i++) {
                        signalNames.put(valueTable.getColumnName(i));
                    }
                    result.put("signalNames", signalNames);

                    for (TestRow row : valueTable) {
                        JSONObject timestep = new JSONObject();

                        timestep.put("time", row.getDescription());


                        JSONArray signals = new JSONArray();
                        for (int i = 0; i < valueTable.getColumns(); i++) {
                            Value value = row.getValue(i);

                            JSONArray signal = new JSONArray();
                            if (value instanceof MatchedValue) {
                                MatchedValue matchedValue = (MatchedValue) value;
                                signal.put(IntFormat.toShortHex(matchedValue.getValue()));
                                signal.put(IntFormat.toShortHex(matchedValue.getExpected().getValue()));
                            } else {
                                signal.put(value.toString());
                                signal.put(value.toString());
                            }
                            signals.put(signal);
                        }
                        timestep.put("signals", signals);
                        timesteps.put(timestep);
                    }
                }
                result.put("timesteps", timesteps);
                results.put(result);
            }
            out.println(results.toString());
        }
        return errorCount;
    }

    /**
     * @return the number of passed tests
     */
    public int getTestsPassed() {
        return testsPassed;
    }

    private CommandLineTester setAllowMissingInputs(boolean allowMissingInputs) {
        this.allowMissingInputs = allowMissingInputs;
        return this;
    }

    /**
     * Sets verbose mode
     *
     * @param verbose true if verbose mode is set
     * @return this for chained calls
     */
    public CommandLineTester setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    /**
     * The test command
     */
    public static class TestCommand extends BasicCommand {
        private final Argument<String> circ;
        private final Argument<String> tests;
        private final Argument<Boolean> allowMissingInputs;
        private final Argument<Boolean> verbose;
        private final Argument<String> jsonOutput;
        private int testsPassed;

        /**
         * Creates a new CLI command
         */
        public TestCommand() {
            super("test");
            circ = addArgument(new Argument<>("circ", "", false));
            tests = addArgument(new Argument<>("tests", "", true));
            allowMissingInputs = addArgument(new Argument<>("allowMissingInputs", false, true));
            verbose = addArgument(new Argument<>("verbose", false, true));
            jsonOutput = addArgument(new Argument<>("json-output", "", true));
        }

        /**
         * Executes the tests and writes to JSON
         * @throws CLIException when there is some error in running the test
         */
        protected void execute() throws CLIException {
            try {
                PrintStream out = System.out;
                boolean fileOutput = jsonOutput.isSet() && !jsonOutput.get().isEmpty();
                if (fileOutput) {
                    out = new PrintStream(new File(jsonOutput.get()));
                }

                try {
                    CommandLineTester clt = new CommandLineTester(new File(circ.get()))
                            .setVerbose(verbose.get())
                            .setAllowMissingInputs(allowMissingInputs.get());
                    if (tests.isSet())
                        clt.useTestCasesFrom(new File(tests.get()));
                    int errors = clt.execute(out);
                    testsPassed = clt.getTestsPassed();
                    if (errors > 0)
                        throw new CLIException(Lang.get("cli_thereAreTestFailures"), errors).hideHelp();
                } finally {
                    if (fileOutput) {
                        out.close();
                    }
                }
            } catch (IOException e) {
                throw new CLIException(Lang.get("cli_errorExecutingTests"), e);
            }
        }

        /**
         * @return the number of tests passed
         */
        public int getTestsPassed() {
            return testsPassed;
        }
    }
}
