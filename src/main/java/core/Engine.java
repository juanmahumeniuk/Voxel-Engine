package core;

import entity.Player;
import render.Renderer;
import world.Chunk;
import world.ChunkMesher;
import world.World;
import world.WorldGenerator;
import world.WorldPersistence;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Engine {
    
    public static void main(String[] args) {
        System.out.println("Starting Voxel Engine...");
        Window window = Window.get();
        window.init();

        long handle = window.getHandle();
        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        try {
            Renderer renderer = new Renderer();
            renderer.init();

            int defaultRadius = 4;
            int viewDistance = 8; // Extra chunks in front
            World world = new World();
            WorldGenerator worldGen = new WorldGenerator(12345L);

            Player player = new Player();
            player.model.x = Chunk.SIZE / 2.0f;
            player.model.y = 80.0f; 
            player.model.z = Chunk.SIZE / 2.0f;
            player.camera.setPosition(0, 0, 0);

            Timer timer = new Timer();
            timer.init();

            while (!glfwWindowShouldClose(handle)) {
                glfwPollEvents();

                if (Input.isKeyDown(GLFW_KEY_ESCAPE)) {
                    glfwSetWindowShouldClose(handle, true);
                }

                glClearColor(0.0f, 0.5f, 0.8f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                float dt = timer.getElapsedTime();
                if (dt > 0.1f) dt = 0.1f;

                double dxMouse = Input.getMouseDx();
                double dyMouse = Input.getMouseDy();
                player.camera.moveRotation((float) dyMouse * 0.15f, (float) dxMouse * 0.15f, 0);
                
                if (player.camera.getRotation().x > 89.0f) player.camera.getRotation().x = 89.0f;
                if (player.camera.getRotation().x < -89.0f) player.camera.getRotation().x = -89.0f;

                player.update(dt, world, 
                        Input.isKeyDown(GLFW_KEY_W), Input.isKeyDown(GLFW_KEY_S), 
                        Input.isKeyDown(GLFW_KEY_A), Input.isKeyDown(GLFW_KEY_D), 
                        Input.isKeyDown(GLFW_KEY_SPACE));

                int camChunkX = (int) Math.floor(player.camera.getPosition().x / Chunk.SIZE);
                int camChunkZ = (int) Math.floor(player.camera.getPosition().z / Chunk.SIZE);

                // Directional Chunk Loading Logic
                float yaw = (float) Math.toRadians(player.camera.getRotation().y);
                float dirX = -(float) Math.sin(yaw);
                float dirZ = -(float) Math.cos(yaw);

                for (int cx = camChunkX - viewDistance; cx <= camChunkX + viewDistance; cx++) {
                    for (int cz = camChunkZ - viewDistance; cz <= camChunkZ + viewDistance; cz++) {
                        
                        // Check if chunk is within a reasonable loading area (weighted towards view direction)
                        float relX = cx - camChunkX;
                        float relZ = cz - camChunkZ;
                        float dist = (float) Math.sqrt(relX*relX + relZ*relZ);
                        
                        // Dot product to check if chunk is "in front"
                        float dot = (relX * dirX + relZ * dirZ) / (dist == 0 ? 1 : dist);
                        
                        boolean shouldLoad = (dist <= defaultRadius) || (dist <= viewDistance && dot > 0.4f);

                        if (shouldLoad && world.getChunk(cx, cz) == null) {
                            Chunk chunk = new Chunk();
                            // Load from disk if exists, otherwise generate
                            byte[] savedData = WorldPersistence.loadChunk(cx, cz);
                            if (savedData != null) {
                                chunk.setData(savedData);
                            } else {
                                worldGen.generate(chunk, cx, cz);
                            }
                            world.addChunk(cx, cz, chunk, ChunkMesher.buildMesh(chunk));
                        }
                    }
                }

                // Cleanup out of radius chunks
                List<int[]> toRemove = new ArrayList<>();
                for (World.ChunkData inst : world.getActiveChunks()) {
                    float relX = inst.cx - camChunkX;
                    float relZ = inst.cz - camChunkZ;
                    float dist = (float) Math.sqrt(relX*relX + relZ*relZ);
                    float dot = (relX * dirX + relZ * dirZ) / (dist == 0 ? 1 : dist);

                    boolean shouldKeep = (dist <= defaultRadius + 1) || (dist <= viewDistance + 1 && dot > 0.2f);
                    
                    if (!shouldKeep) {
                        toRemove.add(new int[]{inst.cx, inst.cz});
                    }
                }
                for (int[] pos : toRemove) {
                    world.removeChunk(pos[0], pos[1]);
                }

                renderer.beginRender(player.camera, window.getWidth(), window.getHeight());
                for (World.ChunkData instance : world.getActiveChunks()) {
                    renderer.renderMesh(instance.mesh, instance.cx * Chunk.SIZE, 0, instance.cz * Chunk.SIZE);
                }
                player.model.render(renderer);
                renderer.endRender();

                glfwSwapBuffers(handle);
            }

            // Cleanup remaining chunks (Save process)
            for (World.ChunkData inst : world.getActiveChunks()) {
                WorldPersistence.saveChunk(inst.cx, inst.cz, inst.chunk.getData());
                if (inst.mesh != null) inst.mesh.cleanup();
            }
            renderer.cleanup();
            WorldPersistence.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        window.destroy();
    }
}
