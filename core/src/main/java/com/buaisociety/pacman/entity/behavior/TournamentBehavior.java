package com.buaisociety.pacman.entity.behavior;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.buaisociety.pacman.entity.*;
import com.buaisociety.pacman.maze.*;
import com.buaisociety.pacman.util.Searcher;
import com.cjcrafter.neat.compute.SimpleCalculator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2ic;

import java.util.List;
import java.util.stream.Collectors;

public class TournamentBehavior implements Behavior {
    private final @NotNull SimpleCalculator calculator;
    private @Nullable PacmanEntity pacman;
    private @Nullable Searcher searcher;

    public TournamentBehavior(@NotNull SimpleCalculator calculator) {
        this.calculator = calculator;
    }

    @NotNull
    @Override
    public Direction getDirection(@NotNull Entity entity) {
        if (pacman == null) {
            pacman = (PacmanEntity) entity;
            searcher = new Searcher(pacman.getMaze());
        }

        // We are going to use these directions a lot for different inputs. Get them all once for clarity and brevity
        Direction forward = pacman.getDirection();
        Direction left = pacman.getDirection().left();
        Direction right = pacman.getDirection().right();
        Direction behind = pacman.getDirection().behind();

        // Input nodes 1, 2, 3, and 4 show if the pacman can move in the forward, left, right, and behind directions
        boolean canMoveForward = pacman.canMove(forward);
        boolean canMoveLeft = pacman.canMove(left);
        boolean canMoveRight = pacman.canMove(right);
        boolean canMoveBehind = pacman.canMove(behind);

        // Use the Searcher to find directions to the closest pellet
        boolean[] directionsToPellet = searcher.getDirectionsToClosestTarget(
            pacman.getTilePosition().x,
            pacman.getTilePosition().y,
            pacman.getDirection(),
            tile -> tile.getState() == TileState.PELLET || tile.getState() == TileState.POWER_PELLET
        );

        // Use the Searcher to find directions to the closest power pellet
        boolean[] directionsToPowerPellet = searcher.getDirectionsToClosestTarget(
            pacman.getTilePosition().x,
            pacman.getTilePosition().y,
            pacman.getDirection(),
            tile -> tile.getState() == TileState.POWER_PELLET
        );

        // Use the Searcher to find directions to the closest fruit
        boolean[] directionsToFruit = searcher.getDirectionsToClosestTarget(
            pacman.getTilePosition().x,
            pacman.getTilePosition().y,
            pacman.getDirection(),
            this::tileContainsFruit
        );

        // Prepare inputs for the neural network
        float[] inputs = new float[16 + 8];
        int index = 0;

        inputs[index++] = canMoveForward ? 1f : 0f;
        inputs[index++] = canMoveLeft ? 1f : 0f;
        inputs[index++] = canMoveRight ? 1f : 0f;
        inputs[index++] = canMoveBehind ? 1f : 0f;

        inputs[index++] = directionsToPellet[0] ? 1f : 0f;
        inputs[index++] = directionsToPellet[1] ? 1f : 0f;
        inputs[index++] = directionsToPellet[2] ? 1f : 0f;
        inputs[index++] = directionsToPellet[3] ? 1f : 0f;

        inputs[index++] = directionsToPowerPellet[0] ? 1f : 0f;
        inputs[index++] = directionsToPowerPellet[1] ? 1f : 0f;
        inputs[index++] = directionsToPowerPellet[2] ? 1f : 0f;
        inputs[index++] = directionsToPowerPellet[3] ? 1f : 0f;

        inputs[index++] = directionsToFruit[0] ? 1f : 0f;
        inputs[index++] = directionsToFruit[1] ? 1f : 0f;
        inputs[index++] = directionsToFruit[2] ? 1f : 0f;
        inputs[index++] = directionsToFruit[3] ? 1f : 0f;

        // New ghost inputs
        addGhostInputs(inputs, index);

        // Get outputs from the neural network
        float[] outputs = calculator.calculate(inputs).join();

        // Determine the direction with the highest output value
        int outputIndex = 0;
        float max = outputs[0];
        for (int i = 1; i < outputs.length; i++) {
            if (outputs[i] > max) {
                max = outputs[i];
                outputIndex = i;
            }
        }

        Direction newDirection;
        switch (outputIndex) {
            case 0:
                newDirection = pacman.getDirection();
                break;
            case 1:
                newDirection = pacman.getDirection().left();
                break;
            case 2:
                newDirection = pacman.getDirection().right();
                break;
            case 3:
                newDirection = pacman.getDirection().behind();
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + outputIndex);
        }

        return newDirection;
    }

    private void addGhostInputs(float[] inputs, int index) {
        Direction[] directions = {Direction.UP, Direction.LEFT, Direction.RIGHT, Direction.DOWN};
        Maze maze = pacman.getMaze();
        List<Entity> ghosts = maze.getEntities().stream()
                .filter(e -> e.getType() == EntityType.GHOST)
                .collect(Collectors.toList());

        for (Direction dir : directions) {
            boolean pathClear = true;
            boolean frightenedGhostAhead = false;

            // Starting from Pacman's current tile
            Tile currentTile = maze.getTile(pacman.getTilePosition());
            Tile tileAhead = maze.getAdjacentTile(currentTile, dir);

            // Traverse tiles in the specified direction
            while (tileAhead != null && tileAhead.isWalkable()) {
                // Check for ghosts on this tile
                for (Entity entity : ghosts) {
                    if (entity.getTilePosition().equals(tileAhead.getPosition())) {
                        GhostEntity ghost = (GhostEntity) entity;
                        if (ghost.isFrightened()) {
                            frightenedGhostAhead = true;
                        } else {
                            pathClear = false;
                        }
                        break; // Exit loop if a ghost is found
                    }
                }
                if (!pathClear || frightenedGhostAhead) {
                    break; // No need to check further tiles
                }
                // Move to the next tile in the same direction
                tileAhead = maze.getAdjacentTile(tileAhead, dir);
            }

            // Add inputs for this direction
            inputs[index++] = pathClear ? 1.0f : 0.0f;
            inputs[index++] = frightenedGhostAhead ? 1.0f : 0.0f;
        }
    }

    private boolean tileContainsFruit(Tile tile) {
        Vector2ic tilePosition = tile.getPosition();
        for (Entity entity : pacman.getMaze().getEntities()) {
            if (entity instanceof FruitEntity && entity.getTilePosition().equals(tilePosition)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(@NotNull SpriteBatch batch) {
        // Optional: Implement if you need to render debug information
    }
}
