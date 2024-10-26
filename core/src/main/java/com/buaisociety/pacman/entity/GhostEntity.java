package com.buaisociety.pacman.entity;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.buaisociety.pacman.entity.behavior.AggressiveChaseBehavior;
import com.buaisociety.pacman.entity.behavior.Behavior;
import com.buaisociety.pacman.entity.behavior.RandomDirectionBehavior;
import com.buaisociety.pacman.entity.behavior.StaticTargetBehavior;
import com.buaisociety.pacman.maze.Maze;
import com.buaisociety.pacman.maze.Tile;
import com.buaisociety.pacman.maze.TileState;
import com.buaisociety.pacman.sprite.GrayscaleSpriteSheet;
import com.buaisociety.pacman.util.NumberUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector2i;

public class GhostEntity extends Entity {

    private static final int[] ELROY_PELLETS = {
        20, 30, 40, 40, 40, 50, 50, 50, 60, 60, 60, 80, 80, 80, 100, 100, 100, 100, 120, 120, 120
    };

    private final GrayscaleSpriteSheet spriteSheet;
    private int animationFrame;

    private final boolean isElroy;  // Blinky becomes "Elroy" and moves faster
    private final Behavior chase;
    private final Behavior scatter;
    private final Behavior frightened;
    private final Behavior eaten;
    private final Color[] colorsAlive;
    private final Color[] colorsFrightened;
    private final Color[] colorsFlash;
    private final Color[] colorsEaten;

    // Vars used during #reset()
    private final @NotNull Vector2i spawnPixel;
    private final @NotNull Direction spawnDirection;
    private final boolean spawnReleased;

    private int localDotCounter;
    private boolean released;
    private @NotNull GhostState state = GhostState.CHASE;
    private @Nullable Direction nextDirection;
    private final Vector2i scatterTile;

    public GhostEntity(@NotNull Maze maze, @NotNull Config config) {
        super(maze, EntityType.GHOST);

        this.isElroy = config.isElroy;
        this.chase = config.chase;
        this.scatterTile = config.scatterTile;
        this.scatter = new StaticTargetBehavior(scatterTile);
        this.frightened = new RandomDirectionBehavior();
        this.eaten = new StaticTargetBehavior(maze.toTileCoords(new Vector2d(config.reviveTile)));

        this.spawnPixel = config.spawnPixel;
        this.spawnDirection = config.spawnDirection;
        this.spawnReleased = config.spawnReleased;
        reset();

        this.spriteSheet = config.spriteSheet;
        this.colorsAlive = config.colorsAlive;
        this.colorsFrightened = config.colorsFrightened;
        this.colorsFlash = config.colorsFlash;
        this.colorsEaten = config.colorsEaten;
    }

    @Override
    public void reset() {
        // Set twice to update lastPosition
        setPosition(new Vector2d(spawnPixel));
        setPosition(new Vector2d(spawnPixel));
        direction = spawnDirection;
        released = spawnReleased;

        state = maze.isGhostChase() ? GhostState.CHASE : GhostState.SCATTER;
        nextDirection = null;
    }

    public @NotNull Vector2i getScatterTile() {
        return scatterTile;
    }

    public int getLocalDotCounter() {
        return localDotCounter;
    }

    public void incrementLocalDotCounter() {
        this.localDotCounter++;
    }

    public boolean isReleased() {
        return released;
    }

    public void setReleased(boolean released) {
        this.released = released;
    }

    public @NotNull GhostState getState() {
        return state;
    }

    public void setState(@NotNull GhostState state) {
        // Don't try to override the power pellet
        if (state == GhostState.FRIGHTENED) {
            if (this.state == GhostState.FRIGHTENED)
                return;
            if (this.state == GhostState.EATEN)
                return;
        }

        // Next direction is no longer valid
        nextDirection = null;

        // Ghosts immediately switch directions when they change state. Sometimes,
        // this switch can cause the ghost to back into a wall. If this happens,
        // we should try other directions
        if (!canMove(direction.behind())) {
            System.out.println("Ghost can't move in direction: " + direction);

            if (canMove(direction.right())) {
                this.direction = direction.right();
            } else if (canMove(direction.left())) {
                this.direction = direction.left();
            }
            // otherwise, don't change direction, just keep going forward
        } else {
            this.direction = direction.behind();
        }

        this.state = state;
    }

    @Override
    public double getSpeed() {
        int level = maze.getLevelManager().getLevel();

        // Allow a handicap to delay the speed changes per-level
        // makes the game easier
        level = Math.max(1, level - maze.getLevelManager().getConfig().handicap);

        // The speed of the ghost changes based on the state
        if (state == GhostState.EATEN) {
            return BASE_SPEED * 2.0;
        } else if (state == GhostState.FRIGHTENED) {
            return BASE_SPEED * switch (level) {
                case 1 -> 0.50;
                case 2, 3, 4 -> 0.55;
                default -> 0.60;
            };
        }

        // When entering a tunnel, the ghosts suffer a severe speed penalty
        if (maze.getTile(getTilePosition()).getState() == TileState.TUNNEL) {
            return BASE_SPEED * switch (level) {
                case 1 -> 0.40;
                case 2, 3, 4 -> 0.45;
                default -> 0.50;
            };
        }

        // Blinky may become "Elroy" and move faster when there are few pellets left
        if (isElroy) {
            int elroyPellets = ELROY_PELLETS[Math.min(level, ELROY_PELLETS.length - 1)];
            if (maze.getPelletsRemaining() < elroyPellets / 2) {
                return BASE_SPEED * switch (level) {
                    case 1 -> 0.85;
                    case 2, 3, 4 -> 0.95;
                    default -> 1.05;
                };
            } else if (maze.getPelletsRemaining() < elroyPellets) {
                return BASE_SPEED * switch (level) {
                    case 1 -> 0.80;
                    case 2, 3, 4 -> 0.90;
                    default -> 1.00;
                };
            }
        }

        // Just normal speeds
        return BASE_SPEED * switch (level) {
            case 1 -> 0.75;
            case 2, 3, 4 -> 0.85;
            default -> 0.95;
        };
    }

    @Override
    public @NotNull Behavior getBehavior() {
        return getBehavior(state);
    }

    public @NotNull Behavior getBehavior(@NotNull GhostState state) {
        return switch (state) {
            case CHASE -> chase;
            case SCATTER -> scatter;
            case FRIGHTENED -> frightened;
            case EATEN -> eaten;
        };
    }

    @Override
    public void update() {
        super.update();
        Tile current = maze.getTile(getTilePosition());
        double speed = getSpeed();

        if (ticksAlive % 5 == 0)
            animationFrame++;

        if (state == GhostState.FRIGHTENED && maze.getFrightenedTimer() == 0)
            setState(maze.isGhostChase() ? GhostState.CHASE : GhostState.SCATTER);

        // When the ghost is still in the ghost pen, it should just bounce
        if (!released) {
            // If moving forward would cause the ghost to leave it's current tile, then
            // we need to change direction.
            speed = 0.5;
            Tile next = maze.getTile(maze.toTileCoords(getPosition().add(direction.getDx() * speed, direction.getDy() * speed)));
            if (!current.equals(next)) {
                direction = direction.behind();
            }

            // fixCenter cannot be used in the ghost pen, since the ghosts are off tiles
            move(direction, speed, false);
            return;
        }

        // While still in the ghost pen, the ghost should move towards the center,
        // then move straight up. TODO: find a way to handle arbitrary ghost pens
        if (current.getState() == TileState.GHOST_PEN && state != GhostState.EATEN) {
            int centerX = maze.getPixelDimensions().x() / 2;

            // When centered on the x-axis, move up to get out
            double dx = position.x() - centerX;
            if (Math.abs(dx) < 0.01) {
                direction = Direction.UP;
                move(direction, 0.5, false); //  TODO: use moveTowards so we don't overshoot
                return;
            }

            direction = dx > 0 ? Direction.LEFT : Direction.RIGHT;
            double x = NumberUtil.moveTowards(position.x(), centerX, 0.5);
            double y = position.y();
            setPosition(new Vector2d(x, y));
            return;
        }

        if (state == GhostState.EATEN) {
            // Look down for an opening to enter the ghost pen
            Tile down = current.getNeighbor(Direction.DOWN);
            if (down.getState() == TileState.GHOST_PEN) {
                // move towards the center of the board, on the x-axis
                Vector2d position = getPosition();
                int centerX = maze.getPixelDimensions().x() / 2;
                position.x = NumberUtil.moveTowards(position.x(), centerX, speed);
                setPosition(position);

                // If we are centered, move down
                if (position.x() == centerX) {
                    direction = Direction.DOWN;
                    move(direction, speed, false);
                }
                return;
            }

            // If we hit a wall, revive
            if (current.getState() == TileState.GHOST_PEN && down.getState() == TileState.WALL) {
                setState(maze.isGhostChase() ? GhostState.CHASE : GhostState.SCATTER);
                return;
            }
        }


        // When the maze changes between scatter and chase mode, the ghost should too
        if (state == GhostState.CHASE && !maze.isGhostChase())
            setState(GhostState.SCATTER);
        else if (state == GhostState.SCATTER && maze.isGhostChase())
            setState(GhostState.CHASE);

        // Basic collision detection
        PacmanEntity pacman = maze.getPacman();
        if (pacman.getTilePosition().equals(getTilePosition())) {
            maze.eatGhost(pacman, this);
        }

        // In ghost may only change direction when it enters an intersection.
        // Since ghosts may not reverse direction, we simply check when we enter
        // a new tile.
        Tile last = maze.getTile(maze.toTileCoords(lastPosition));
        if (!last.equals(current) || nextDirection == null) {
            Behavior behavior = getBehavior();
            nextDirection = behavior.getDirection(this);
        }

        // When we are locked into a new direction, we should move towards the center
        // of the tile, then move in that direction.
        if (nextDirection != direction) {
            Vector2d center = new Vector2d(current.getCenterPixel());
            if (!position.equals(center, 0.1)) {
                double x = NumberUtil.moveTowards(position.x(), center.x(), speed);
                double y = NumberUtil.moveTowards(position.y(), center.y(), speed);
                setPosition(new Vector2d(x, y));
                return;
            }

            direction = nextDirection;
        }

        // This should never occur
        if (!canMove(direction)) {
            System.out.println("Ghost is permanently stuck: " + direction);
            return;
        }

        move(direction, speed, true);
    }


    @Override
    public void render(@NotNull SpriteBatch batch) {
        // The colors change based on the state of the ghost
        Color[] colors = switch (state) {
            case CHASE, SCATTER -> colorsAlive;
            case FRIGHTENED -> maze.getFrightenedTimer() < 100
                ? (maze.getFrightenedTimer() % 20 < 10) ? colorsFlash : colorsFrightened
                : colorsFrightened;
            case EATEN -> colorsEaten;
        };

        // ghost-sprite.png
        int spriteX;
        int spriteY;
        if (state == GhostState.CHASE || state == GhostState.SCATTER) {
            spriteX = direction.ordinal() * 2 + animationFrame % 2;
            spriteY = 0;
        } else if (state == GhostState.FRIGHTENED) {
            spriteX = animationFrame % 2;
            spriteY = 1;
        } else {
            spriteX = 2 + direction.ordinal();
            spriteY = 1;
        }

        spriteSheet.setColors(colors);
        spriteSheet.setCurrentTile(spriteX, spriteY);
        int pixelX = (int) position.x() - spriteSheet.getTileSize().x() / 2 + 1;
        int pixelY = (int) position.y() - spriteSheet.getTileSize().y() / 2 + 1;
        spriteSheet.render(batch, pixelX, pixelY);
    }

    @Override
    public void dispose() {
        spriteSheet.dispose();
    }

    public boolean isFrightened() {
        return state == GhostState.FRIGHTENED;
    }


    public static class Config {
        public boolean isElroy = false;
        public @NotNull GrayscaleSpriteSheet spriteSheet = new GrayscaleSpriteSheet(new Texture("sprites/ghost-sprite.png"), 20);
        public @NotNull Behavior chase = new AggressiveChaseBehavior();
        public @NotNull Vector2i scatterTile = new Vector2i();
        public @NotNull Vector2i spawnPixel = new Vector2i();
        public @NotNull Direction spawnDirection = Direction.UP;
        public boolean spawnReleased = false;
        public @NotNull Vector2i reviveTile = new Vector2i();
        public @NotNull Color[] colorsAlive = new Color[]{};
        public @NotNull Color[] colorsFrightened = new Color[]{};
        public @NotNull Color[] colorsFlash = new Color[]{};
        public @NotNull Color[] colorsEaten = new Color[]{};
    }

    
}
