package com.buaisociety.pacman.util;

import com.buaisociety.pacman.entity.Direction;
import com.buaisociety.pacman.entity.Entity;
import com.buaisociety.pacman.entity.GhostEntity;
import com.buaisociety.pacman.maze.Maze;
import com.buaisociety.pacman.maze.Tile;
import com.buaisociety.pacman.maze.TileState;
import org.joml.Vector2i;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Predicate;

public class Searcher {

    private final Maze maze;

    public Searcher(Maze maze) {
        this.maze = maze;
    }

    /**
     * Performs BFS to find the directions to the closest target based on a given predicate.
     *
     * @param startX          The starting x-coordinate (tile position)
     * @param startY          The starting y-coordinate (tile position)
     * @param startDirection  The initial facing direction
     * @param targetPredicate The predicate to determine if a tile is a target
     * @return A boolean array indicating directions to the closest target [forward, left, right, behind]
     */
    public boolean[] getDirectionsToClosestTarget(int startX, int startY, Direction startDirection, Predicate<Tile> targetPredicate) {
        Tile[][] tiles = maze.getTiles(); // Access the tiles array
        int height = tiles.length;
        int width = tiles[0].length;

        boolean[][] visited = new boolean[height][width];
        Queue<BFSNode> queue = new LinkedList<>();

        // Initialize directions
        boolean[] directionsToTarget = new boolean[4]; // [forward, left, right, behind]

        // Enqueue starting position with null initial direction
        queue.add(new BFSNode(startX, startY, null, 0));

        int closestDistance = Integer.MAX_VALUE;

        while (!queue.isEmpty()) {
            BFSNode node = queue.poll();
            int x = node.x;
            int y = node.y;
            Direction initialDirection = node.initialDirection;
            int distance = node.distance;

            // Skip if out of bounds or already visited
            if (x < 0 || x >= width || y < 0 || y >= height || visited[y][x]) {
                continue;
            }

            visited[y][x] = true;
            Tile tile = maze.getTile(x, y);

            // Check if we found a target
            if (targetPredicate.test(tile)) {
                if (distance < closestDistance) {
                    closestDistance = distance;
                    directionsToTarget = new boolean[4]; // Reset directions
                }
                if (distance == closestDistance && initialDirection != null) {
                    if (initialDirection == startDirection) {
                        directionsToTarget[0] = true; // Forward
                    } else if (initialDirection == startDirection.left()) {
                        directionsToTarget[1] = true; // Left
                    } else if (initialDirection == startDirection.right()) {
                        directionsToTarget[2] = true; // Right
                    } else if (initialDirection == startDirection.behind()) {
                        directionsToTarget[3] = true; // Behind
                    }
                }
                continue; // Continue to find all targets at the same minimal distance
            }

            // Enqueue neighboring tiles
            for (Direction dir : Direction.values()) {
                int newX = (x + dir.getDx() + width) % width;
                int newY = (y + dir.getDy() + height) % height;

                if (!visited[newY][newX]) {
                    Tile neighborTile = maze.getTile(newX, newY);
                    TileState neighborState = neighborTile.getState();

                    if (neighborState.isPassable() || targetPredicate.test(neighborTile)) {
                        // Determine initial direction
                        Direction newInitialDirection = initialDirection;
                        if (initialDirection == null) {
                            newInitialDirection = dir;
                        }
                        queue.add(new BFSNode(newX, newY, newInitialDirection, distance + 1));
                    }
                }
            }
        }

        return directionsToTarget;
    }

    // Helper class for BFS nodes
    private static class BFSNode {
        int x;
        int y;
        Direction initialDirection;
        int distance;

        BFSNode(int x, int y, Direction initialDirection, int distance) {
            this.x = x;
            this.y = y;
            this.initialDirection = initialDirection;
            this.distance = distance;
        }
    }
}
