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
 *  - Calculates the nearest position based on the matching BSSID and their signal strength in the database
 *    to the user's input.
 *
 * ------------------------------------------------------------------------------------
 */

package com.example.indoornavigation;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.*;

/**
 * Manages the SQLite database of known locations and Wi-Fi access point fingerprints.
 * Provides helper methods to find the nearest location based on RSSI scans,
 * and to look up coordinates by room number or location name.
 */
public class DBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "IndoorNavigation.db";
    private static final int DATABASE_VERSION = 2;

    private static final Map<String, String> roomNumberToLocation = new HashMap<>();
    // Maps room numbers (e.g., "AT-5001") to hallway location IDs
    static {
        // 5th Floor mappings
        roomNumberToLocation.put("AT-5044", "Hallway160005");
        roomNumberToLocation.put("AT-5042", "Hallway220005");
        roomNumberToLocation.put("AT-5035.1", "Hallway280005");
        roomNumberToLocation.put("AT-5041", "Hallway320005");
        roomNumberToLocation.put("AT-5040", "Hallway400005");
        roomNumberToLocation.put("AT-5035.2", "Hallway420005");
        roomNumberToLocation.put("AT-5033", "Hallway560005");
        // For AT-5036 and AT-5028, using the combined mapping:
        roomNumberToLocation.put("AT-5036", "Hallway621205");
        roomNumberToLocation.put("AT-5028", "Hallway621205");
        roomNumberToLocation.put("AT-5031", "Hallway621805");
        roomNumberToLocation.put("AT-5027", "Hallway501805");
        roomNumberToLocation.put("AT-5024", "Hallway461805");
        roomNumberToLocation.put("AT-5025", "Hallway461805");
        roomNumberToLocation.put("AT-5023", "Hallway421805");
        roomNumberToLocation.put("AT-5022", "Hallway421805");
        roomNumberToLocation.put("AT-5020", "Hallway401805");
        roomNumberToLocation.put("AT-5021", "Hallway401805");
        roomNumberToLocation.put("AT-5019", "Hallway381805");
        roomNumberToLocation.put("AT-5018", "Hallway381805");
        roomNumberToLocation.put("AT-5016", "Hallway341805");
        roomNumberToLocation.put("AT-5017", "Hallway341805");
        roomNumberToLocation.put("AT-5014", "Hallway321805");
        roomNumberToLocation.put("AT-5015", "Hallway321805");
        roomNumberToLocation.put("AT-5012", "Hallway281805");
        roomNumberToLocation.put("AT-5013", "Hallway281805");
        roomNumberToLocation.put("AT-5010", "Hallway241805");
        roomNumberToLocation.put("AT-5011", "Hallway241805");
        roomNumberToLocation.put("AT-5008", "Hallway221805");
        roomNumberToLocation.put("AT-5009", "Hallway221805");
        roomNumberToLocation.put("AT-5006", "Hallway181805");
        roomNumberToLocation.put("AT-5007", "Hallway181805");
        roomNumberToLocation.put("AT-5004", "Hallway161805");
        roomNumberToLocation.put("AT-5005", "Hallway161805");
        roomNumberToLocation.put("AT-5003", "Hallway141805");
        roomNumberToLocation.put("AT-5002", "Hallway141805");
        roomNumberToLocation.put("AT-5001", "Hallway121805");
        // 4th Floor mappings
        roomNumberToLocation.put("AT-4020", "Hallway120004");
        roomNumberToLocation.put("AT-4019", "Hallway280004");
        roomNumberToLocation.put("AT-4016", "Hallway480004");
        roomNumberToLocation.put("AT-4015", "Hallway560004");
        roomNumberToLocation.put("AT-4014", "Hallway580004");
        roomNumberToLocation.put("AT-4013", "Hallway620604");
        roomNumberToLocation.put("AT-4012", "Hallway621404");
        roomNumberToLocation.put("AT-4011", "Hallway621604");
        roomNumberToLocation.put("AT-4010", "Hallway621604");
        roomNumberToLocation.put("AT-4009", "Hallway621604");
        roomNumberToLocation.put("AT-4008", "Hallway621604");
        roomNumberToLocation.put("AT-4007", "Hallway621604");
        roomNumberToLocation.put("AT-4006", "Hallway582204");
        roomNumberToLocation.put("AT-4005", "Hallway542204");
        roomNumberToLocation.put("AT-4004.1", "Hallway502204");
        roomNumberToLocation.put("AT-4004.2", "Hallway382204");
        roomNumberToLocation.put("AT-4003", "Hallway302204");
        roomNumberToLocation.put("AT-4002", "Hallway302204");
        roomNumberToLocation.put("AT-4001", "Hallway122204");
        // 3rd Floor mappings
        roomNumberToLocation.put("AT-3010", "Hallway120003");
        roomNumberToLocation.put("AT-3009", "Hallway260003");
        roomNumberToLocation.put("AT-3006", "Hallway540003");
        roomNumberToLocation.put("AT-3005", "Hallway620603");
        roomNumberToLocation.put("AT-3004", "Hallway622203");
        roomNumberToLocation.put("AT-3003", "Hallway602203");
        roomNumberToLocation.put("AT-3002", "Hallway502203");
        roomNumberToLocation.put("AT-3001", "Hallway302203");

    }

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create Locations table: id, unique name, x/y/z coords
        db.execSQL("CREATE TABLE Locations (id INTEGER PRIMARY KEY AUTOINCREMENT, locationName TEXT NOT NULL, x INTEGER, y INTEGER, z INTEGER)");
        // Create AccessPoints table: BSSID, SSID, avg RSSI, foreign key to Locations
        db.execSQL("CREATE TABLE AccessPoints (id INTEGER PRIMARY KEY AUTOINCREMENT, BSSID TEXT NOT NULL, SSID TEXT NOT NULL, AvgRSSI INTEGER, LocationID INTEGER, FOREIGN KEY(LocationID) REFERENCES Locations(id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Make sure there's no overlap of the similar application signature
        db.execSQL("DROP TABLE IF EXISTS AccessPoints");
        db.execSQL("DROP TABLE IF EXISTS Locations");
        onCreate(db);
    }

    /**
     * Given a map of scanned BSSID -> RSSI values, pick the location with
     * the best fingerprint match (highest aggregated RSSI similarity).
     */
    public String getNearestLocation(Map<String, Integer> scannedBSSIDs) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT BSSID, AvgRSSI, LocationID FROM AccessPoints", null);
        Map<Integer, Integer> locationStrength = new HashMap<>();

        // Score each location by comparing scan to stored AvgRSSI values
        while (cursor.moveToNext()) {
            String bssid = cursor.getString(0);
            int stored = cursor.getInt(1);
            int locId = cursor.getInt(2);
            if (scannedBSSIDs.containsKey(bssid)) {
                int diff = Math.abs(stored - scannedBSSIDs.get(bssid));
                locationStrength.put(locId, locationStrength.getOrDefault(locId, 0) + (100 - diff));
            }
        }
        cursor.close();

        // Pick location with max score
        int bestId = -1, max = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Integer> e : locationStrength.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); bestId = e.getKey(); }
        }
        if (bestId != -1) {
            Cursor c = db.rawQuery("SELECT locationName FROM Locations WHERE id = ?", new String[]{String.valueOf(bestId)});
            if (c.moveToFirst()) { String name = c.getString(0); c.close(); return name; }
        }
        return null;
    }

    /**
     * Lookup X,Y,Z coordinates by either a hallway name or a room number.
     * @return int[]{x,y,z} or null if not found.
     */
    public int[] getLocationCoordinates(String input) {
        String locName = input;
        if (roomNumberToLocation.containsKey(input)) locName = roomNumberToLocation.get(input);
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT x, y, z FROM Locations WHERE locationName = ?", new String[]{locName});
        if (cursor.moveToFirst()) {
            int x = cursor.getInt(0);
            int y = cursor.getInt(1);
            int z = cursor.getInt(2);
            cursor.close();
            return new int[]{x, y, z};
        }
        return null;
    }
}
