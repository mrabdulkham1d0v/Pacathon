package com.buaisociety.pacman.maze;

import com.buaisociety.pacman.entity.Direction;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.Objects;

/**
 * Represents a tile in the maze.
 */
public class Tile {

    private final @NotNull Maze maze;
    private final @NotNull Vector2i position;
    private @NotNull TileState state;

    public Tile(@NotNull Maze maze, @NotNull Vector2i position, @NotNull TileState state) {
        this.maze = maze;
        this.position = position;
        this.state = state;
    }

    /**
     * Returns the maze that this tile is a part of.
     *
     * @return the maze that this tile is a part of.
     */
    public @NotNull Maze getMaze() {
        return maze;
    }

    /**
     * Returns the tile coordinates of this tile.
     *
     * <p>The coordinates are in tile units, where the bottom-left tile is (0, 0).
     *
     * @return the tile coordinates of this tile.
     */
    public @NotNull Vector2ic getPosition() {
        return position;
    }

    /**
     * Returns the current contents of this tile.
     *
     * @return the current state of the tile.
     */
    public @NotNull TileState getState() {
        return state;
    }

    /**
     * Sets the contents of this tile.
     *
     * <p>This is used to remove {@link TileState#PELLET pellets} and
     * {@link TileState#POWER_PELLET power pellets} from the maze as pacman eats
     * them.
     *
     * @param state the new state of the tile.
     */
    public void setState(@NotNull TileState state) {
        this.state = state;
    }

    /**
     * Returns the position of the pixel in the center of this tile.
     *
     * <p>Since each tile is a 8x8 grid, the original Pacman game arbitrarily
     * defines the center to be the pixel in the bottom-left corner of the center.
     *
     * @return the position of the center pixel of this tile
     */
    public @NotNull Vector2i getCenterPixel() {
        int x = position.x * Maze.TILE_SIZE + Maze.TILE_SIZE / 2 - 1;
        int y = position.y * Maze.TILE_SIZE + Maze.TILE_SIZE / 2 - 1;
        return new Vector2i(x, y);
    }

    /**
     * Returns the neighbor tile in the given direction.
     *
     * @param direction the direction to get the neighbor tile
     * @return the neighbor tile in the given direction
     */
    public @NotNull Tile getNeighbor(@NotNull Direction direction) {
        return maze.getTile(position.x + direction.getDx(), position.y + direction.getDy());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tile tile = (Tile) o;
        return Objects.equals(maze, tile.maze) && Objects.equals(position, tile.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maze, position);
    }

    public boolean isWalkable() {
        return state != TileState.WALL;
    }
    
}
