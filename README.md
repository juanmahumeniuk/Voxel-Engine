# Voxel Engine

A high-performance voxel engine built with Java 17 and LWJGL 3, featuring procedural terrain generation, infinite worlds, and persistent storage.

## 🚀 Features

- **Procedural Generation**: Robust terrain generation using Perlin Noise with Fractal Brownian Motion (FBM).
- **Dynamic Biomes**: Distinct biomes including Snow, Desert (Sand), and Temperate (Grass/Dirt/Stone) based on moisture and temperature maps.
- **Infinite World**: Directional chunk loading and unloading logic weighted towards the player's view direction.
- **Persistence**: Efficient chunk storage system using Zlib compression and `RandomAccessFile` for fast I/O.
- **High Density Rendering**: Optimized rendering pipeline for voxel-based meshes.
- **FPS Controls**: First-person camera with smooth movement and mouse look.

## 🛠 Tech Stack

- **Java 17**: Core language.
- **LWJGL 3** (Lightweight Java Game Library): For OpenGL rendering and window management (GLFW).
- **JOML** (Java OpenGL Math Library): For 3D mathematics.
- **Gradle**: Build and dependency management.

## 📦 Getting Started

### Prerequisites

- Java 17 or higher
- Gradle (included via Gradle Wrapper)

### Installation & Run

1. Clone the repository:
   ```bash
   git clone https://github.com/juanmahumeniuk/Voxel-Engine.git
   cd Voxel-Engine
   ```

2. Build and run the engine:
   ```bash
   ./gradlew run
   ```

## 🎮 Controls

| Key | Action |
|-----|--------|
| **W / A / S / D** | Movement |
| **SPACE** | Jump / Fly Up |
| **MOUSE** | Look Around |
| **ESCAPE** | Exit Engine |

## 📁 Data Storage

Chunks are saved in the `world_save/` directory using a custom `.vxl` format. Each region supports up to 64x64 chunks with sector-based alignment and Zlib compression.
