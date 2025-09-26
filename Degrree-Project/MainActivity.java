// MainActivity.java
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
    private static final String UNITY_IP = "172.20.10.2"; // Replace with your Unity computer's IP
    private static final int UNITY_PORT = 8123;            // Replace with the port Unity is listening on

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

        List<int[]> shortestPath = dbHelper.findShortestPathAStar(currentX, currentY, currentZ,
                destinationCoordinates[0], destinationCoordinates[1], destinationCoordinates[2]);

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


// DBHelper.java

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
 *      on 01/03/2025
 *
 * Associated files: MainActivity.java
 *
 * Description of the file's functionality:
 *  - The file is responsible for processing the input data passed from the MainActivity.java file.
 *  - It provides the definition of the database.
 *  - Calculates the nearest position based on the matching BSSID and theirs signal strength in the database
 *    to the user's input.
 *  - A* algorithm for finding the shortest path from the user's current position.
 *
 * ------------------------------------------------------------------------------------
 */

 package com.example.indoornavigation;

 import android.content.Context;
 import android.database.Cursor;
 import android.database.sqlite.SQLiteDatabase;
 import android.database.sqlite.SQLiteOpenHelper;
 import java.util.*;
 
 class DBHelper extends SQLiteOpenHelper {
     // Database name and its version for upgrade purposes
     private static final String DATABASE_NAME = "IndoorNavigation.db";
     private static final int DATABASE_VERSION = 2;
 
     // Movement cost constants for the navigation calculations
     private static final double BASE_STEP_COST = 2;      // cost per 2m step
     private static final double TURN_PENALTY = 1;        // cost penalty for changing direction
     private static final double FLOOR_CHANGE_COST = 10;  // cost to change floors via elevator
 
     // Constructor: Pass the application context along with the database's information
     public DBHelper(Context context) {
         super(context, DATABASE_NAME, null, DATABASE_VERSION);
     }
 
     // Function is called whenever the database is created for the first time
     @Override
     public void onCreate(SQLiteDatabase db) {
         // Locations table
         db.execSQL("CREATE TABLE Locations (id INTEGER PRIMARY KEY AUTOINCREMENT, locationName TEXT NOT NULL, x INTEGER, y INTEGER, z INTEGER)");
         // Access Points table
         db.execSQL("CREATE TABLE AccessPoints (id INTEGER PRIMARY KEY AUTOINCREMENT, BSSID TEXT NOT NULL, SSID TEXT NOT NULL, AvgRSSI INTEGER, LocationID INTEGER, FOREIGN KEY(LocationID) REFERENCES Locations(id))");
     }
 
     // Function is called when the database needs to be updated
     @Override
     public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
         // Drop the Access Points table if there is an existing table
         db.execSQL("DROP TABLE IF EXISTS AccessPoints");
         // Drop the Location table if there is an existing table
         db.execSQL("DROP TABLE IF EXISTS Locations");
         // Call the onCreate() to rebuild the database schema
         onCreate(db);
     }
 
     // ------------------------------------------------------------------------
     // 1) Get nearest location from Wi-Fi scans
     // Wi-Fi scan results to determine the best matching location based on signal strength differences
     // ------------------------------------------------------------------------
     public String getNearestLocation(Map<String, Integer> scannedBSSIDs) {
         // Create an instance of the database
         SQLiteDatabase db = this.getReadableDatabase();
         // Create an instance of type Cursor in order to query the Access Points table rows
         Cursor cursor = db.rawQuery("SELECT BSSID, AvgRSSI, LocationID FROM AccessPoints", null);
 
         // A HashMap to store all the signal strength scores for each location
         HashMap<Integer, Integer> locationStrength = new HashMap<>();
         // Loop through each row
         while (cursor.moveToNext()) {
             String bssid = cursor.getString(0); // Get the stored BSSID
             int storedRSSI = cursor.getInt(1);  // Get the stored RSSI
             int locationId = cursor.getInt(2);  // Get the LocationID related to this access point
 
             // If the scanned WIFI results contains the same BSSID, then calculate the differences in signal strength
             if (scannedBSSIDs.containsKey(bssid)) {
                 int diff = Math.abs(storedRSSI - scannedBSSIDs.get(bssid));
                 // Higher score == Better estimation
                 locationStrength.put(locationId, locationStrength.getOrDefault(locationId, 0) + (100 - diff));
             }
         }
         // Release query instance
         cursor.close();
 
         int bestLocation = -1;
         int maxStrength = Integer.MIN_VALUE;
         // Loop through each location and its computed signal strength score
         for (Map.Entry<Integer, Integer> entry : locationStrength.entrySet()) {
             if (entry.getValue() > maxStrength) {
                 maxStrength = entry.getValue();
                 bestLocation = entry.getKey();
             }
         }
         // If a best location is found, retrieve its name from the Locations table
         if (bestLocation != -1) {
             Cursor locCursor = db.rawQuery("SELECT locationName FROM Locations WHERE id = ?", new String[]{String.valueOf(bestLocation)});
             if (locCursor.moveToFirst()) {
                 String detectedLocation = locCursor.getString(0);
                 locCursor.close();
                 return detectedLocation;
             }
         }
         return null;
     }
 
     // ------------------------------------------------------------------------
     // 2) Get coordinates (x,y,z) from a location name
     // Retrieve the x, y, and z coordinates of a location based on its name
     // ------------------------------------------------------------------------
     public int[] getLocationCoordinates(String locationName) {
         // Create an instance of the database
         SQLiteDatabase db = this.getReadableDatabase();
         // Create an instance of type Cursor in order to query the Locations table rows
         Cursor cursor = db.rawQuery("SELECT x, y, z FROM Locations WHERE locationName = ?", new String[]{locationName});
         if (cursor.moveToFirst()) {
             int x = cursor.getInt(0);
             int y = cursor.getInt(1);
             int z = cursor.getInt(2);
             cursor.close();
             return new int[]{x, y, z};
         }
         return null;
     }
 
     // ------------------------------------------------------------------------
     // 3) A* Algorithm with multi-floor + intersection-turn logic
     // ------------------------------------------------------------------------
     public List<int[]> findShortestPathAStar(int startX, int startY, int startZ,
                                              int goalX,  int goalY,  int goalZ) {
         // Create a priority queue to hold nodes which to be explored, sorted by their estimated cost
         PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
         // Create a Map to hold the explored nodes
         Map<String, Node> allNodes = new HashMap<>();
         // Create a set to hold nodes that have already processed
         Set<String> closedSet = new HashSet<>();
 
         // Create start node
         Node startNode = new Node(startX, startY, startZ,
                 0,
                 heuristic(startX, startY, startZ, goalX, goalY, goalZ),
                 null,
                 null,
                 false);
 
         // Add the root node to the set
         openSet.add(startNode);
 
         // Record the root node in the allNodes map using a unique key
         allNodes.put(nodeKey(startX, startY, startZ, startNode.direction, startNode.intersectionTurn), startNode);
 
         // Loop through the openSet's nodes for processing
         // Stop when there are no more nodes to be processed
         while (!openSet.isEmpty()) {
             // Pick the node with the smallest cost
             Node current = openSet.poll();
             // Add to the closedSet to avoid redundancy
             closedSet.add(nodeKey(current.x, current.y, current.z, current.direction, current.intersectionTurn));
 
             // Check if the node is the goal node
             if (current.x == goalX && current.y == goalY && current.z == goalZ) {
                 return reconstructPath(current);
             }
 
             // Loop through all the possible neighbors from the current node
             for (Node neighbor : getValidHallwayNeighbors(current)) {
                 String neighborKey = nodeKey(neighbor.x, neighbor.y, neighbor.z, neighbor.direction, neighbor.intersectionTurn);
                 // If the neighbor has already been processed, then skip
                 if (closedSet.contains(neighborKey)) {
                     continue;
                 }
 
                 // COMPUTING THE STEP COST
                 // First assign the base step cost ( BASE_STEP_COST = 2 )
                 double stepCost = BASE_STEP_COST;
                 // Check if the path requires elevator, then assigns higher cost
                 if (neighbor.direction == Direction.ELEVATOR) {
                     stepCost = FLOOR_CHANGE_COST;
                 }
                 // If the neighbor isn't an intersection turn
                 // But shorter path near to an intersection, add a turn penalty
                 else if (!neighbor.intersectionTurn &&
                         current.direction != null &&
                         !Objects.equals(current.direction, neighbor.direction)) {
                     stepCost += TURN_PENALTY;
                 }
                 // tentative cost: g(Cost)
                 double tentativeG = current.gCost + stepCost;
                 // If this neighbor is new or there is another a cheaper way to get here, update its costs and parent pointer
                 if (!allNodes.containsKey(neighborKey) || tentativeG < allNodes.get(neighborKey).gCost) {
                     // Update fCost to be the sum of the cost so far and the heuristic estimate to the goal
                     neighbor.gCost = tentativeG;
                     neighbor.fCost = tentativeG + heuristic(neighbor.x, neighbor.y, neighbor.z, goalX, goalY, goalZ);
                     // Mark current as the parent for path reconstruction
                     neighbor.parent = current;
                     // Add the neighbor to the open set for future processing
                     openSet.add(neighbor);
                     // Update the allNodes map with the neighbor
                     allNodes.put(neighborKey, neighbor);
                 }
             }
         }
 
         // No path found
         return new ArrayList<>();
     }
 
     // Manhattan-based heuristic
     private double heuristic(int x1, int y1, int z1, int x2, int y2, int z2) {
         // 0.5 to serve as an admissible heuristic
         return 0.5 * (Math.abs(x1 - x2) + Math.abs(y1 - y2) + Math.abs(z1 - z2));
     }
 
     // A unique string key for a node based on its coordinates, movement direction, and if it's an intersection turn
     private String nodeKey(int x, int y, int z, Direction direction, boolean intersectionTurn) {
         return x + "-" + y + "-" + z + "-" + (direction != null ? direction.name() : "null")
                 + "-" + (intersectionTurn ? "T" : "F");
     }
 
     // ------------------------------------------------------------------------
     // 4) "Hallway + Intersection" rules for BOTH floors
     //    Create valid neighbor nodes based on building's layout
     //    Create logic to handle additional floor
     // ------------------------------------------------------------------------
     private List<Node> getValidHallwayNeighbors(Node current) {
         // Create a list to store all valid neighboring nodes
         List<Node> neighbors = new ArrayList<>();
         int x = current.x; // Current node x
         int y = current.y; // Current node y
         int z = current.z; // Current node z
 
         // -------------------------------------------------------
         // Elevator logic (for floor 4 and 5)
         // Check if the current position is at an elevator
         // -------------------------------------------------------
         if ((x == 0 && y == 0) || (x == 66 && y == 0)) {
             int[] floors = {4, 5}; // Consider only these two floors elevator
             for (int floor : floors) {
                 // Only consider a move to elevator that changes the floor
                 if (floor != z) {
                     // Assign the elevator direction
                     neighbors.add(new Node(x, y, floor, 0, 0, null, Direction.ELEVATOR, false));
                 }
             }
         }
 
         // -------------------------------------------------------
         // 5th Floor Rules (z == 5)
         // -------------------------------------------------------
         if (z == 5) {
             // Hallway 1: y==0, x in [0..66]
             if (y == 0 && x >= 0 && x <= 66) {
                 // East/west steps
                 if (x + 2 <= 66) {
                     neighbors.add(new Node(x + 2, y, z, 0, 0, null, Direction.EAST, false));
                 }
                 if (x - 2 >= 0) {
                     neighbors.add(new Node(x - 2, y, z, 0, 0, null, Direction.WEST, false));
                 }
                 // Intersection A => (62,0,5)
                 if (x == 62) {
                     neighbors.add(new Node(62, 2, z, 0, 0, null, Direction.NORTH, true));
                 }
                 // Intersection D => (12,0,5)
                 if (x == 12) {
                     neighbors.add(new Node(12, 2, z, 0, 0, null, Direction.NORTH, true));
                 }
             }
             // Hallway 2: x==62, y in [0..18]
             if (x == 62 && y >= 0 && y <= 18) {
                 if (y + 2 <= 18) {
                     neighbors.add(new Node(x, y + 2, z, 0, 0, null, Direction.NORTH, false));
                 }
                 if (y - 2 >= 0) {
                     neighbors.add(new Node(x, y - 2, z, 0, 0, null, Direction.SOUTH, false));
                 }
                 // Intersection B => (62,18,5)
                 if (y == 18) {
                     neighbors.add(new Node(60, 18, z, 0, 0, null, Direction.WEST, true));
                 }
             }
             // Hallway 3: y==18, x in [12..62]
             if (y == 18 && x >= 12 && x <= 62) {
                 if (x + 2 <= 62) {
                     neighbors.add(new Node(x + 2, y, z, 0, 0, null, Direction.EAST, false));
                 }
                 if (x - 2 >= 12) {
                     neighbors.add(new Node(x - 2, y, z, 0, 0, null, Direction.WEST, false));
                 }
                 // Intersection C => (12,18,5)
                 if (x == 12) {
                     neighbors.add(new Node(12, 16, z, 0, 0, null, Direction.SOUTH, true));
                 }
             }
             // Hallway 4: x==12, y in [2..18]
             if (x == 12 && y >= 2 && y <= 18) {
                 if (y + 2 <= 18) {
                     neighbors.add(new Node(x, y + 2, z, 0, 0, null, Direction.NORTH, false));
                 }
                 if (y - 2 >= 2) {
                     neighbors.add(new Node(x, y - 2, z, 0, 0, null, Direction.SOUTH, false));
                 }
                 // Intersection => (12,2,5)
                 if (y == 2) {
                     neighbors.add(new Node(12, 0, z, 0, 0, null, null, true));
                 }
             }
         }
 
         // -------------------------------------------------------
         // 4th Floor Rules (z == 4)
         // -------------------------------------------------------
         if (z == 4) {
             // Hallway 1 (4th floor): y == 0, x in [0..66]
             if (y == 0 && x >= 0 && x <= 66) {
                 // East/west steps
                 if (x + 2 <= 66) {
                     neighbors.add(new Node(x + 2, y, z, 0, 0, null, Direction.EAST, false));
                 }
                 if (x - 2 >= 0) {
                     neighbors.add(new Node(x - 2, y, z, 0, 0, null, Direction.WEST, false));
                 }
                 // Intersection D => (12,0,4) leads north to Hallway4
                 if (x == 12) {
                     neighbors.add(new Node(12, 2, z, 0, 0, null, Direction.NORTH, true));
                 }
                 // Intersection A => (62,0,4) leads north to Hallway2
                 if (x == 62) {
                     neighbors.add(new Node(62, 2, z, 0, 0, null, Direction.NORTH, true));
                 }
             }
 
             // Hallway 2 (4th floor): x == 62, y in [0..22]
             if (x == 62 && y >= 0 && y <= 22) {
                 if (y + 2 <= 22) {
                     neighbors.add(new Node(x, y + 2, z, 0, 0, null, Direction.NORTH, false));
                 }
                 if (y - 2 >= 0) {
                     neighbors.add(new Node(x, y - 2, z, 0, 0, null, Direction.SOUTH, false));
                 }
                 // Intersection B => (62,22,4) turning west to Hallway3
                 if (y == 22) {
                     neighbors.add(new Node(60, 22, z, 0, 0, null, Direction.WEST, true));
                 }
             }
 
             // Hallway 3 (4th floor): y == 22, x in [10..62]
             if (y == 22 && x >= 10 && x <= 62) {
                 // East/west steps
                 if (x + 2 <= 62) {
                     neighbors.add(new Node(x + 2, y, z, 0, 0, null, Direction.EAST, false));
                 }
                 if (x - 2 >= 10) {
                     neighbors.add(new Node(x - 2, y, z, 0, 0, null, Direction.WEST, false));
                 }
                 // Intersection C => (10,22,4) turning south to Hallway4
                 if (x == 10) {
                     neighbors.add(new Node(10, 20, z, 0, 0, null, Direction.SOUTH, true));
                 }
             }
 
             // Hallway 4 (4th floor): x == 12, y in [2..22]
             if (x == 12 && y >= 2 && y <= 22) {
                 // Move north/south in 2-meter steps
                 if (y + 2 <= 22) {
                     neighbors.add(new Node(x, y + 2, z, 0, 0, null, Direction.NORTH, false));
                 }
                 if (y - 2 >= 2) {
                     neighbors.add(new Node(x, y - 2, z, 0, 0, null, Direction.SOUTH, false));
                 }
                 // If y == 2, that connects back to (12,0,4)
                 if (y == 2) {
                     neighbors.add(new Node(12, 0, z, 0, 0, null, null, true));
                 }
             }
         }
 
         return neighbors;
     }
 
     // RECONSTRUCTION OF THE PATH
     // Reconstruct the path by backtracking from the goal node to the start node using parent pointers
     private List<int[]> reconstructPath(Node node) {
         // Create a list to store the path coordinates
         List<int[]> path = new ArrayList<>();
         // Traverse backwards through each node's parent until the start node has been reached
         while (node != null) {
             path.add(0, new int[]{node.x, node.y, node.z});
             node = node.parent;
         }
         return path;
     }
 
     // ------------------------------------------------------------------------
     // Direction enum + Node class
     // ------------------------------------------------------------------------
     private enum Direction {
         // Represent the different movement directions in the building
         NORTH, SOUTH, EAST, WEST, ELEVATOR
     }
 
     // Node class represents a single state (or point) in the search space for A*
     private static class Node {
         int x, y, z; // Coordinates in the building (x, y, and floor z)
         // gCost: the cost from the start to this node
         // fCost: the total estimated cost
         double gCost, fCost;
         Node parent;
         Direction direction;
         boolean intersectionTurn;
 
         Node(int x, int y, int z,
              double gCost, double fCost,
              Node parent,
              Direction direction,
              boolean intersectionTurn)
         {
             this.x = x;
             this.y = y;
             this.z = z;
             this.gCost = gCost;
             this.fCost = fCost;
             this.parent = parent;
             this.direction = direction;
             this.intersectionTurn = intersectionTurn;
         }
     }
 }
