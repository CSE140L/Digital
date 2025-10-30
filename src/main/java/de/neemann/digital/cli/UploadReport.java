/*
 * Copyright (c) 2023 Anish Govind.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.cli;

import de.neemann.digital.cli.cli.Argument;
import de.neemann.digital.cli.cli.BasicCommand;
import de.neemann.digital.cli.cli.CLIException;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.graphics.GraphicSVG;
import de.neemann.digital.draw.graphics.Export;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Properties;

/**
 * A CLI tool to upload test and statistics reports to a server.
 */
public class UploadReport extends BasicCommand {
    private final Argument<String> url;
    private final Argument<String> studentId;
    private final Argument<String> labNumber;
    private final Argument<String> testReport;
    private final Argument<String> statsReport;
    private final Argument<String> digFile;

    /**
     * Creates a new UploadReport command.
     */
    public UploadReport() {
        super("upload-report");
        url = addArgument(new Argument<>("url", "", false));
        studentId = addArgument(new Argument<>("student-id", "", false));
        labNumber = addArgument(new Argument<>("lab-number", "", false));
        testReport = addArgument(new Argument<>("test-report", "", true));
        statsReport = addArgument(new Argument<>("stats-report", "", true));
        digFile = addArgument(new Argument<>("dig-file", "", true));
    }

    /**
     * Executes the upload command, sending reports to the server.
     * @throws CLIException if an error occurs during execution.
     */
    @Override
    protected void execute() throws CLIException {
        String authToken = readAuthToken();

        String responseUrl = null;

        if (testReport.isSet()) {
            responseUrl = uploadFile(authToken, testReport.get(), "tests");
        }

        if (statsReport.isSet()) {
            String url = uploadFile(authToken, statsReport.get(), "stats");
            if (responseUrl == null) {
                responseUrl = url;
            }
        }

        if (digFile.isSet()) {
            String url = uploadDigFile(authToken, digFile.get());
            if (responseUrl == null) {
                responseUrl = url;
            }
        }

        if (responseUrl != null) {
            System.out.println("Report uploaded to: " + responseUrl);
        } else {
            System.out.println("No files specified to upload.");
        }
    }

    /**
     * Uploads a file to a specified report endpoint.
     *
     * @param authToken the authorization token.
     * @param filePath the path to the file to upload.
     * @param reportType the type of report (e.g., "tests", "stats").
     * @return the URL of the uploaded report.
     * @throws CLIException if an error occurs during file reading or upload.
     */
    private String uploadFile(String authToken, String filePath, String reportType) throws CLIException {
        try {
            String content = new String(Files.readAllBytes(new File(filePath).toPath()));
            String endpoint = url.get() + "/reports/" + labNumber.get() + "/" + studentId.get() + "/" + reportType;
            return doPost(endpoint, content, authToken);
        } catch (IOException e) {
            throw new CLIException("Error reading file: " + filePath, e);
        }
    }

    /**
     * Generates an SVG from a .dig file, and uploads it to the image endpoint.
     *
     * @param authToken the authorization token.
     * @param filePath the path to the .dig file.
     * @return the URL of the uploaded image.
     * @throws CLIException if an error occurs during processing or upload.
     */
    private String uploadDigFile(String authToken, String filePath) throws CLIException {
        try {
            File circuitFile = new File(filePath);
            Circuit circuit = new CircuitLoader(filePath).getCircuit();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Export exporter = new Export(circuit, o -> new GraphicSVG(o, new de.neemann.digital.core.element.ElementAttributes()));
            exporter.export(baos);

            String svgContent = Base64.getEncoder().encodeToString(baos.toByteArray());

            JSONObject json = new JSONObject();
            json.put("circuit_name", circuitFile.getName());
            json.put("svg", svgContent);

            String endpoint = url.get() + "/reports/" + labNumber.get() + "/" + studentId.get() + "/image";
            return doPost(endpoint, json.toString(), authToken);
        } catch (Exception e) {
            throw new CLIException("Error processing or uploading .dig file: " + filePath, e);
        }
    }

    /**
     * Performs an HTTP POST request.
     *
     * @param endpoint the URL to send the request to.
     * @param body the request body.
     * @param authToken the authorization token.
     * @return the response from the server.
     * @throws CLIException if an error occurs during the request.
     */
    private String doPost(String endpoint, String body, String authToken) throws CLIException {
        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    return response.toString();
                }
            } else {
                throw new CLIException("HTTP POST request failed with response code: " + responseCode, 1);
            }
        } catch (IOException e) {
            throw new CLIException("Error during HTTP POST request to " + endpoint, e);
        }
    }

    /**
     * Reads the authorization token from the .env file in the project root.
     *
     * @return the authorization token.
     * @throws CLIException if the .env file is not found or the token is missing.
     */
    private String readAuthToken() throws CLIException {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            throw new CLIException(".env file not found in the project root.", 1);
        }
        try (FileInputStream fis = new FileInputStream(envFile)) {
            Properties properties = new Properties();
            properties.load(fis);
            String token = properties.getProperty("REPORT_SERVER_AUTH_TOKEN");
            if (token == null || token.trim().isEmpty()) {
                throw new CLIException("REPORT_SERVER_AUTH_TOKEN not found in .env file.", 1);
            }
            return token.trim();
        } catch (IOException e) {
            throw new CLIException("Error reading .env file", e);
        }
    }
}
