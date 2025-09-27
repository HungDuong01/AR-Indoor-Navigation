/* ------------------------------------------------------------------------------------
 *
 * Indoor Navigation Project
 *
 * Related Documents:
 *    Specification Document
 *    Design Document
 *
 * File created by
 *      Chan Hung Duong
 *      on 20/02/2025
 *
 * Associated files: activity_main.xml
 *
 * Description of the file's functionality:
 *  - TBD
 *
 * Now supports entering a room number (e.g., "AT-5001") instead of a location name.
 *
 * ------------------------------------------------------------------------------------
 */

package com.example.indoornavigation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity {
    private Button scanButton, navigateButton;
    private EditText destinationInput;
    private TextView currentPositionDisplay, navigationInstructions, pathDisplay;
    private WifiManager wifiManager;
    private DBHelper dbHelper;
    private int currentX, currentY, currentZ;
    private String currentLocation;

    // Unity connection details - update these as necessary
    private static final String UNITY_IP = "172.20.10.2"; // Unity computer's IP
    private static final int UNITY_PORT = 8123;            // The port Unity is listening on

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermissions();
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        scanButton = findViewById(R.id.btnScan);
        navigateButton = findViewById(R.id.btnNavigate);
        destinationInput = findViewById(R.id.destinationInput);
        currentPositionDisplay = findViewById(R.id.currentPosition);
        navigationInstructions = findViewById(R.id.navigationInstructions);
        pathDisplay = findViewById(R.id.pathDisplay);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WifiManager.class);
        dbHelper = new DBHelper(this);

        scanButton.setOnClickListener(v -> detectCurrentPosition());
        navigateButton.setOnClickListener(v -> navigateToDestination());
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    private void detectCurrentPosition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        if (wifiManager == null) {
            Toast.makeText(this, "Wi-Fi Manager unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean success = wifiManager.startScan();
        if (!success) {
            Toast.makeText(this, "Wi-Fi scan failed.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<ScanResult> results = wifiManager.getScanResults();
        if (results == null || results.isEmpty()) {
            Toast.makeText(this, "No Wi-Fi networks detected.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Integer> scannedBSSIDs = new HashMap<>();
        for (ScanResult result : results) {
            scannedBSSIDs.put(result.BSSID, result.level);
        }

        String detectedLocation = dbHelper.getNearestLocation(scannedBSSIDs);
        if (detectedLocation != null) {
            currentLocation = detectedLocation;
            int[] coordinates = dbHelper.getLocationCoordinates(detectedLocation);
            currentX = coordinates[0];
            currentY = coordinates[1];
            currentZ = coordinates[2];

            currentPositionDisplay.setText("Current Position: " + detectedLocation + " (" + currentX + "," + currentY + "," + currentZ + ")");
        } else {
            currentPositionDisplay.setText("Current Position: Unknown");
        }
    }

    private void navigateToDestination() {
        String destination = destinationInput.getText().toString().trim();
        int[] destinationCoordinates = dbHelper.getLocationCoordinates(destination);

        if (destinationCoordinates == null) {
            Toast.makeText(this, "Invalid destination!", Toast.LENGTH_SHORT).show();
            return;
        }

        List<int[]> shortestPath = PathFinder.findShortestPathAStar(
                currentX, currentY, currentZ,
                destinationCoordinates[0], destinationCoordinates[1], destinationCoordinates[2]
        );
        if (shortestPath.isEmpty()) {
            pathDisplay.setText("No valid path found.");
            return;
        }

        StringBuilder pathString = new StringBuilder("Shortest Path:\n");
        for (int[] node : shortestPath) {
            pathString.append("(")
                    .append(node[0]).append(", ")
                    .append(node[1]).append(", ")
                    .append(node[2]).append(")\n");
        }

        pathDisplay.setText(pathString.toString());

        // Send the path to Unity for AR simulation via HTTP POST
        sendPathToUnity(shortestPath);
    }

    /**
     * Sends the list of path coordinates to the Unity application via an HTTP POST request.
     * The path is serialized as a JSON array of objects, each with x, y, and z coordinates.
     */
    private void sendPathToUnity(List<int[]> path) {
        new Thread(() -> {
            try {
                URL url = new URL("http://" + UNITY_IP + ":" + UNITY_PORT + "/receivePath/");
                Log.d("MainActivity", "Connecting to: " + url.toString());

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");

                // Create a JSON array for the path coordinates
                JSONArray jsonArray = new JSONArray();
                for (int[] node : path) {
                    JSONObject point = new JSONObject();
                    point.put("x", node[0]);
                    point.put("y", node[1]);
                    point.put("z", node[2]);
                    jsonArray.put(point);
                }

                String jsonString = jsonArray.toString();
                Log.d("MainActivity", "Sending JSON: " + jsonString);

                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(jsonString);
                writer.flush();
                writer.close();

                int responseCode = connection.getResponseCode();
                Log.d("MainActivity", "Response code from Unity server: " + responseCode);
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("MainActivity", "Error sending path to Unity: " + e.getMessage());
            }
        }).start();
    }
}
