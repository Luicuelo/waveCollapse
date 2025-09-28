# Wave Function Collapse Algorithm Visualization

This project is a JavaFX implementation of the Wave Function Collapse (WFC) algorithm for procedural pattern generation. It visualizes how the algorithm works by placing circuit tiles according to their connection constraints.

## Overview

The Wave Function Collapse algorithm is a constraint-based procedural generation technique that creates patterns by gradually reducing possibilities until a valid solution is found. This implementation focuses on circuit tile generation, where each tile has specific connection points that must match adjacent tiles.

![Floor Plan Example](/png/sampleFloorPlan.png)

## Features

- Interactive visualization of the WFC algorithm
- Real-time animation of tile placement
- Support for multiple tile types with different connection patterns
- Derived tile generation through rotation and mirroring
- Step-by-step execution control (restart, stop, step)
- Configurable canvas size

## How It Works

1. **Initialization**: The algorithm starts with a grid where each cell can potentially be any tile type.
2. **Constraint Propagation**: When a tile is placed, it constrains the possible tiles in adjacent cells.
3. **Tile Selection**: The algorithm selects the cell with the fewest possibilities (lowest entropy) to collapse next.
4. **Backtracking**: If a contradiction is reached (no valid tiles for a cell), the algorithm backtracks and tries different options.

## Tile System

Each tile has four sides (top, right, bottom, left) with three connection zones each. The zones are represented by colors:
- **G** - Green
- **B** - Blue
- **W** - White
- **D** - Dark

Tiles can only be placed adjacent to each other if their connection zones match.

## Controls

- **Restart**: Clears the board and starts a new generation
- **Stop**: Halts the current generation process

## Requirements

- Java 8 or higher
- JavaFX support

## Building and Running

1. Compile the Java files:
   ```
   javac -d bin src/principal/*.java
   ```

2. Run the application:
   ```
   java -cp bin principal.WaveFunction
   ```

## Code Structure

- `TitlesManager.java`: Manages tile definitions, loading, and board operations
- `Renderer.java`: Handles the visualization and rendering of tiles
- `WaveFunction.java`: Main application class that implements the WFC algorithm

## Customization

You can customize the canvas size by passing command-line arguments:
```
java -cp bin principal.WaveFunction --width=1024 --height=768
```

## License

This project is open source and available under the MIT License.