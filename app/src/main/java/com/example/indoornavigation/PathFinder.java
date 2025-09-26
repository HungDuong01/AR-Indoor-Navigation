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
 * Associated files: DBHelper
 *
 * Description of the file's functionality:
 *  - Finding the shortest path from the user current position to the
 *    desired destination.
 *  - The function takes 2 main inputs:
 *      1. Current Position of the user
 *      2. Entered destination from user
 *
 * ------------------------------------------------------------------------------------
 */

package com.example.indoornavigation;

import java.util.*;

/**
 * Static utility class for computing shortest paths in a multi-floor building using the A* algorithm.
 * Splits elevator, hallway, and turn penalties into configurable costs.
 */
public class PathFinder {
    private static final double BASE_STEP_COST = 2;
    private static final double TURN_PENALTY = 1;
    private static final double FLOOR_CHANGE_COST = 10;
    /**
     * Finds the shortest path from start to goal coordinates (x,y,z) using A*.
     * @param startX starting X coordinate
     * @param startY starting Y coordinate
     * @param startZ starting floor
     * @param goalX  target X coordinate
     * @param goalY  target Y coordinate
     * @param goalZ  target floor
     * @return a List of int arrays, each representing [x,y,z] along the path, or empty list if no path
     */
    public static List<int[]> findShortestPathAStar(int startX, int startY, int startZ,
                                                    int goalX, int goalY, int goalZ) {
        // Open set sorted by lowest fCost (gCost + heuristic)
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
        Map<String, Node> allNodes = new HashMap<>(); // track best known nodes
        Set<String> closedSet = new HashSet<>();   // visited nodes
        // Initialize start node with gCost=0
        Node startNode = new Node(startX, startY, startZ,
                0,
                heuristic(startX, startY, startZ, goalX, goalY, goalZ),
                null,
                null,
                false);

        openSet.add(startNode);
        allNodes.put(nodeKey(startNode), startNode);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            closedSet.add(nodeKey(current));

            // If the goal has been reached, reconstruct and return the path
            if (current.x == goalX && current.y == goalY && current.z == goalZ) {
                return reconstructPath(current);
            }
            // Explore neighbors
            for (Node neighbor : getValidHallwayNeighbors(current)) {
                String key = nodeKey(neighbor);
                if (closedSet.contains(key)) continue; // skip if already evaluated

                double stepCost = calculateStepCost(current, neighbor);
                double tentativeG = current.gCost + stepCost;
                // If new path to neighbor is shorter, or neighbor is unvisited, update it
                if (!allNodes.containsKey(key) || tentativeG < allNodes.get(key).gCost) {
                    neighbor.gCost = tentativeG;
                    neighbor.fCost = tentativeG + heuristic(neighbor.x, neighbor.y, neighbor.z, goalX, goalY, goalZ);
                    neighbor.parent = current;
                    openSet.add(neighbor);
                    allNodes.put(key, neighbor);
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Computes movement cost between current and neighbor nodes, including penalties.
     */
    private static double calculateStepCost(Node current, Node neighbor) {
        if (neighbor.direction == Direction.ELEVATOR) {
            return FLOOR_CHANGE_COST; // elevator overrides step cost entirely
        } else if (current.direction != null && current.direction != neighbor.direction) {
            return BASE_STEP_COST + TURN_PENALTY; // adding turn penalty
        } else {
            return BASE_STEP_COST; // normal move
        }
    }

    /**
     * Manhattan-based heuristic scaled by 0.5 to guide A*.
     */
    private static double heuristic(int x1, int y1, int z1, int x2, int y2, int z2) {
        return 0.5 * (Math.abs(x1 - x2) + Math.abs(y1 - y2) + Math.abs(z1 - z2));
    }

    /**
     * Builds a unique string key for use in maps/sets from node properties.
     */
    private static String nodeKey(Node n) {
        return n.x + "-" + n.y + "-" + n.z + "-"
                + (n.direction != null ? n.direction.name() : "null") + "-"
                + (n.intersectionTurn ? "T" : "F");
    }

    /**
     * Backtracks parent pointers to reconstruct the full path from start to goal.
     */
    private static List<int[]> reconstructPath(Node node) {
        List<int[]> path = new ArrayList<>();
        while (node != null) {
            path.add(0, new int[]{node.x, node.y, node.z});
            node = node.parent;
        }
        return path;
    }

    /**
     * Encapsulates building-specific rules for valid neighbor expansion.
     */
    private static List<Node> getValidHallwayNeighbors(Node current) {
        List<Node> neighbors = new ArrayList<>();
        int x = current.x;
        int y = current.y;
        int z = current.z;

        // Elevator logic (for floors 3, 4 and 5)
        if ((x == 0 && y == 0) || (x == 66 && y == 0)) {
            int[] floors = {3, 4, 5};
            for (int floor : floors) {
                if (floor != z) {
                    neighbors.add(new Node(x, y, floor, 0, 0, null, Direction.ELEVATOR, false));
                }
            }
        }

        // 5th Floor Rules (z == 5)
        if (z == 5) {
            if (y == 0 && x >= 0 && x <= 66) {
                if (x + 2 <= 66) {
                    neighbors.add(new Node(x + 2, y, z, 0, 0, null, Direction.EAST, false));
                }
                if (x - 2 >= 0) {
                    neighbors.add(new Node(x - 2, y, z, 0, 0, null, Direction.WEST, false));
                }
                if (x == 62) {
                    neighbors.add(new Node(62, 2, z, 0, 0, null, Direction.NORTH, true));
                }
                if (x == 12) {
                    neighbors.add(new Node(12, 2, z, 0, 0, null, Direction.NORTH, true));
                }
            }
            if (x == 62 && y >= 0 && y <= 18) {
                if (y + 2 <= 18) {
                    neighbors.add(new Node(x, y + 2, z, 0, 0, null, Direction.NORTH, false));
                }
                if (y - 2 >= 0) {
                    neighbors.add(new Node(x, y - 2, z, 0, 0, null, Direction.SOUTH, false));
                }
                if (y == 18) {
                    neighbors.add(new Node(60, 18, z, 0, 0, null, Direction.WEST, true));
                }
            }
            if (y == 18 && x >= 12 && x <= 62) {
                if (x + 2 <= 62) {
                    neighbors.add(new Node(x + 2, y, z, 0, 0, null, Direction.EAST, false));
                }
                if (x - 2 >= 12) {
                    neighbors.add(new Node(x - 2, y, z, 0, 0, null, Direction.WEST, false));
                }
                if (x == 12) {
                    neighbors.add(new Node(12, 16, z, 0, 0, null, Direction.SOUTH, true));
                }
            }
            if (x == 12 && y >= 2 && y <= 18) {
                if (y + 2 <= 18) {
                    neighbors.add(new Node(x, y + 2, z, 0, 0, null, Direction.NORTH, false));
                }
                if (y - 2 >= 2) {
                    neighbors.add(new Node(x, y - 2, z, 0, 0, null, Direction.SOUTH, false));
                }
                if (y == 2) {
                    neighbors.add(new Node(12, 0, z, 0, 0, null, null, true));
                }
            }
        }

        // 4th Floor Rules (z == 4)
        if (z == 4) {
            if (y == 0 && x >= 0 && x <= 66) {
                if (x + 2 <= 66) {
                    neighbors.add(new Node(x + 2, y, z, 0, 0, null, Direction.EAST, false));
                }
                if (x - 2 >= 0) {
                    neighbors.add(new Node(x - 2, y, z, 0, 0, null, Direction.WEST, false));
                }
                if (x == 62) {
                    neighbors.add(new Node(62, 2, z, 0, 0, null, Direction.NORTH, true));
                }
                if (x == 10) {
                    neighbors.add(new Node(10, 2, z, 0, 0, null, Direction.NORTH, true));
                }
            }
            if (x == 62 && y >= 0 && y <= 22) {
                if (y + 2 <= 22) {
                    neighbors.add(new Node(x, y + 2, z, 0, 0, null, Direction.NORTH, false));
                }
                if (y - 2 >= 0) {
                    neighbors.add(new Node(x, y - 2, z, 0, 0, null, Direction.SOUTH, false));
                }
                if (y == 22) {
                    neighbors.add(new Node(60, 22, z, 0, 0, null, Direction.WEST, true));
                }
            }
            if (y == 22 && x >= 10 && x <= 62) {
                if (x + 2 <= 62) {
                    neighbors.add(new Node(x + 2, y, z, 0, 0, null, Direction.EAST, false));
                }
                if (x - 2 >= 10) {
                    neighbors.add(new Node(x - 2, y, z, 0, 0, null, Direction.WEST, false));
                }
                if (x == 10) {
                    neighbors.add(new Node(10, 20, z, 0, 0, null, Direction.SOUTH, true));
                }
            }
            if (x == 10 && y >= 2 && y <= 22) {
                if (y + 2 <= 22) {
                    neighbors.add(new Node(x, y + 2, z, 0, 0, null, Direction.NORTH, false));
                }
                if (y - 2 >= 2) {
                    neighbors.add(new Node(x, y - 2, z, 0, 0, null, Direction.SOUTH, false));
                }
                if (y == 2) {
                    neighbors.add(new Node(10, 0, z, 0, 0, null, null, true));
                }
            }
        }

        // 3rd Floor Rules (z == 3)
        if (z == 3) {
            if (y == 0 && x >= 0 && x <= 66) {
                if (x + 2 <= 66) {
                    neighbors.add(new Node(x + 2, y, z, 0, 0, null, Direction.EAST, false));
                }
                if (x - 2 >= 0) {
                    neighbors.add(new Node(x - 2, y, z, 0, 0, null, Direction.WEST, false));
                }
                if (x == 62) {
                    neighbors.add(new Node(62, 2, z, 0, 0, null, Direction.NORTH, true));
                }
                if (x == 10) {
                    neighbors.add(new Node(10, 2, z, 0, 0, null, Direction.NORTH, true));
                }
            }
            if (x == 62 && y >= 0 && y <= 22) {
                if (y + 2 <= 22) {
                    neighbors.add(new Node(x, y + 2, z, 0, 0, null, Direction.NORTH, false));
                }
                if (y - 2 >= 0) {
                    neighbors.add(new Node(x, y - 2, z, 0, 0, null, Direction.SOUTH, false));
                }
                if (y == 22) {
                    neighbors.add(new Node(60, 22, z, 0, 0, null, Direction.WEST, true));
                }
            }
            if (y == 22 && x >= 10 && x <= 62) {
                if (x + 2 <= 62) {
                    neighbors.add(new Node(x + 2, y, z, 0, 0, null, Direction.EAST, false));
                }
                if (x - 2 >= 10) {
                    neighbors.add(new Node(x - 2, y, z, 0, 0, null, Direction.WEST, false));
                }
                if (x == 10) {
                    neighbors.add(new Node(10, 20, z, 0, 0, null, Direction.SOUTH, true));
                }
            }
            if (x == 10 && y >= 2 && y <= 22) {
                if (y + 2 <= 22) {
                    neighbors.add(new Node(x, y + 2, z, 0, 0, null, Direction.NORTH, false));
                }
                if (y - 2 >= 2) {
                    neighbors.add(new Node(x, y - 2, z, 0, 0, null, Direction.SOUTH, false));
                }
                if (y == 2) {
                    neighbors.add(new Node(10, 0, z, 0, 0, null, null, true));
                }
            }
        }

        return neighbors;
    }

    /**
     * Direction of travel used for turn penalty calculations.
     */
    private enum Direction { NORTH, SOUTH, EAST, WEST, ELEVATOR }

    /**
     * Internal node class tracking position, costs, parent, and movement metadata.
     */
    private static class Node {
        int x, y, z;    // coordinates & floor
        double gCost, fCost;     // accumulated and projected costs
        Node parent;    // backpointer for path reconstruction
        Direction direction;     // direction of entry to this node
        boolean intersectionTurn;    // true if this node is an intersection turn

        Node(int x, int y, int z,
             double gCost, double fCost,
             Node parent,
             Direction direction,
             boolean intersectionTurn) {
            this.x = x; this.y = y; this.z = z;
            this.gCost = gCost; this.fCost = fCost;
            this.parent = parent;
            this.direction = direction;
            this.intersectionTurn = intersectionTurn;
        }
    }
}
