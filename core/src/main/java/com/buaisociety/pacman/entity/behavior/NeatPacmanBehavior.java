package com.buaisociety.pacman.entity.behavior;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.buaisociety.pacman.maze.Maze;
import com.buaisociety.pacman.maze.Tile;
import com.buaisociety.pacman.maze.TileState;
import com.buaisociety.pacman.sprite.DebugDrawing;
import com.cjcrafter.neat.Client;
import com.buaisociety.pacman.entity.Direction;
import com.buaisociety.pacman.entity.Entity;
import com.buaisociety.pacman.entity.EntityType;
import com.buaisociety.pacman.entity.FruitEntity;
import com.buaisociety.pacman.entity.GhostEntity;
import com.buaisociety.pacman.entity.PacmanEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.stream.Collectors;


import com.buaisociety.pacman.util.Searcher;


public class NeatPacmanBehavior implements Behavior {

    private final @NotNull Client client;
    private @Nullable PacmanEntity pacman;
    private @Nullable Searcher searcher;
    // Score modifiers help us maintain "multiple pools" of points.
    // This is great for training, because we can take away points from
    // specific pools of points instead of subtracting from all.
    private int scoreModifier = 0;

    private int numberUpdatesSinceLastScore = 0;
    private int lastScore = 0;

    public NeatPacmanBehavior(@NotNull Client client) {
        this.client = client;
    }

    /**
     * Returns the desired direction that the entity should move towards.
     *
     * @param entity the entity to get the direction for
     * @return the desired direction for the entity
     */
    @NotNull
    @Override
    public Direction getDirection(@NotNull Entity entity) {
        if (pacman == null) {
            pacman = (PacmanEntity) entity;
            searcher = new Searcher(pacman.getMaze());
        }

        // SPECIAL TRAINING CONDITIONS
        // TODO: Make changes here to help with your training...
        int newScore = pacman.getMaze().getLevelManager().getScore();
        if (newScore > lastScore) {
            lastScore = newScore;
            numberUpdatesSinceLastScore = 0;
        } 

        if (numberUpdatesSinceLastScore++ > 60 * 10) {
            pacman.kill();
            return Direction.UP;
        }

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


        // END OF SPECIAL TRAINING CONDITIONS

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

        // Prepare inputs for the neural network
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
        float[] outputs = client.getCalculator().calculate(inputs).join();

        // Determine the direction with the highest output value
        index = 0;
        float max = outputs[0];
        for (int i = 1; i < outputs.length; i++) {
            if (outputs[i] > max) {
                max = outputs[i];
                index = i;
            }
        }

        Direction newDirection;
        switch (index) {
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
                throw new IllegalStateException("Unexpected value: " + index);
        }   

        // Update the client's score (fitness)
        int score = pacman.getMaze().getLevelManager().getScore() + scoreModifier;

        // Add bonus for eating power pellets and fruits
        // Positive rewards
        // Positive rewards
        score += pacman.getPelletsEaten() * 50;         // Increased from 10 to 50
        score += pacman.getPowerPelletsEaten() * 100;   // Increased from 50 to 100
        score += pacman.getGhostsEaten() * 200;         // Same as before

        if (pacman.hasAdvancedToNextLevel()) {
            score += 500;
        }

        // Negative rewards
        if (!pacman.isAlive()) {
            score -= 100; // Pacman was caught by a ghost
        }


        // Time penalty
        // Time penalty
        // Apply time penalty only after 1000 ticks
        if (pacman.getTicksAlive() > 1000) {
            score -= (pacman.getTicksAlive() - 1000) * 0.1;
        }
        
        // Survival reward
        score += pacman.getTicksAlive() * 0.5; // Reward per tick


        client.setScore(score);
        return newDirection;
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


private void addGhostInputs(float[] inputs, int index) {
    Direction[] directions = {Direction.UP, Direction.LEFT, Direction.RIGHT, Direction.DOWN};
    Maze maze = pacman.getMaze();
    java.util.List<Entity> ghosts = maze.getEntities().stream()
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




    @Override
    public void render(@NotNull SpriteBatch batch) {
        // TODO: You can render debug information here
        /*
        if (pacman != null) {
            DebugDrawing.outlineTile(batch, pacman.getMaze().getTile(pacman.getTilePosition()), Color.RED);
            DebugDrawing.drawDirection(batch, pacman.getTilePosition().x() * Maze.TILE_SIZE, pacman.getTilePosition().y() * Maze.TILE_SIZE, pacman.getDirection(), Color.RED);
        }
         */
    }
}
