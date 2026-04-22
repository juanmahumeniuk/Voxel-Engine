package core;

import entity.Player;
import entity.WeaponSystem;

import render.FpsOverlay;
import render.BulletTracerRenderer;
import render.MainMenu;
import render.RenderLod;
import render.Renderer;
import render.SkyRenderer;
import render.WeaponOverlay;
import world.Chunk;
import world.ChunkMeshExecutor;
import world.ChunkMesher;
import world.ChunkStreaming;
import world.World;
import world.WorldGenerator;
import world.WorldPersistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Engine {
    
    public static void main(String[] args) {
        System.out.println("Starting Voxel Engine...");
        Window window = Window.get();
        window.init();

        long handle = window.getHandle();

        ChunkMeshExecutor meshExecutor = null;
        MainMenu mainMenu = null;
        try {
            boolean vSyncEnabled = false;
            mainMenu = new MainMenu();
            mainMenu.setCanContinue(WorldPersistence.hasSavedWorld());
            mainMenu.setVSyncEnabled(vSyncEnabled);
            glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);

            boolean prevUp = false;
            boolean prevDown = false;
            boolean prevLeft = false;
            boolean prevRight = false;
            boolean prevEnter = false;
            boolean prevEscape = false;
            boolean startGame = false;

            while (!glfwWindowShouldClose(handle) && !startGame) {
                glfwPollEvents();

                boolean up = Input.isKeyDown(GLFW_KEY_UP) || Input.isKeyDown(GLFW_KEY_W);
                boolean down = Input.isKeyDown(GLFW_KEY_DOWN) || Input.isKeyDown(GLFW_KEY_S);
                boolean left = Input.isKeyDown(GLFW_KEY_LEFT) || Input.isKeyDown(GLFW_KEY_A);
                boolean right = Input.isKeyDown(GLFW_KEY_RIGHT) || Input.isKeyDown(GLFW_KEY_D);
                boolean enter = Input.isKeyDown(GLFW_KEY_ENTER);
                boolean escape = Input.isKeyDown(GLFW_KEY_ESCAPE);

                if (up && !prevUp) {
                    mainMenu.selectPrev();
                }
                if (down && !prevDown) {
                    mainMenu.selectNext();
                }
                if ((left && !prevLeft) || (right && !prevRight)) {
                    if (mainMenu.getSelectedAction() == MainMenu.ACTION_OPTIONS) {
                        vSyncEnabled = !vSyncEnabled;
                        glfwSwapInterval(vSyncEnabled ? 1 : 0);
                        mainMenu.setVSyncEnabled(vSyncEnabled);
                        mainMenu.setStatusMessage("VSync actualizado.");
                    }
                }
                if (enter && !prevEnter) {
                    int action = mainMenu.getSelectedAction();
                    if (action == MainMenu.ACTION_NEW_WORLD) {
                        WorldPersistence.clearWorld();
                        mainMenu.setCanContinue(false);
                        mainMenu.setStatusMessage("Se creo un mundo nuevo.");
                        startGame = true;
                    } else if (action == MainMenu.ACTION_CONTINUE_WORLD) {
                        if (WorldPersistence.hasSavedWorld()) {
                            mainMenu.setStatusMessage("Cargando mundo guardado...");
                            startGame = true;
                        } else {
                            mainMenu.setStatusMessage("No hay mundo guardado para continuar.");
                        }
                    } else if (action == MainMenu.ACTION_OPTIONS) {
                        vSyncEnabled = !vSyncEnabled;
                        glfwSwapInterval(vSyncEnabled ? 1 : 0);
                        mainMenu.setVSyncEnabled(vSyncEnabled);
                        mainMenu.setStatusMessage("VSync actualizado.");
                    }
                }
                if (escape && !prevEscape) {
                    glfwSetWindowShouldClose(handle, true);
                }

                prevUp = up;
                prevDown = down;
                prevLeft = left;
                prevRight = right;
                prevEnter = enter;
                prevEscape = escape;

                glClearColor(0.03f, 0.03f, 0.05f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                mainMenu.render(window.getWidth(), window.getHeight());
                glfwSwapBuffers(handle);
            }

            mainMenu.cleanup();
            mainMenu = null;
            if (glfwWindowShouldClose(handle)) {
                return;
            }
            glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

            Renderer renderer = new Renderer();
            renderer.init();
            SkyRenderer skyRenderer = new SkyRenderer();
            skyRenderer.init();
            FpsOverlay fpsOverlay = new FpsOverlay();
            WeaponOverlay weaponOverlay = new WeaponOverlay();
            BulletTracerRenderer tracerRenderer = new BulletTracerRenderer();

            /** Omnidirectional chunks around camera (streaming / simulation). */
            int defaultRadius = 7;
            /** Chunk columns to keep loaded ahead (wider than draw distance). */
            int viewDistance = 15;
            /** Forward cone: lower = wider wedge. */
            float loadForwardDotMin = 0.2f;
            /** Chebyshev chunk distance for drawing only. */
            int renderChebyshevChunks = 9;
            /** Chunk data + generation per frame; meshing runs on {@link ChunkMeshExecutor} threads. */
            int maxChunksToGeneratePerFrame = 20;
            World world = new World();
            WorldGenerator worldGen = new WorldGenerator(12345L);
            meshExecutor = new ChunkMeshExecutor();

            Player player = new Player();
            WeaponSystem weaponSystem = new WeaponSystem();
            player.model.x = Chunk.SIZE / 2.0f;
            player.model.y = 80.0f;
            player.model.z = Chunk.SIZE / 2.0f;
            player.camera.setPosition(0, 0, 0);

            int spawnCx = Math.floorDiv((int) Math.floor(player.model.x), Chunk.SIZE);
            int spawnCz = Math.floorDiv((int) Math.floor(player.model.z), Chunk.SIZE);
            int spawnPreloadRadius = 4;
            ChunkStreaming.preloadSquare(world, worldGen, spawnCx, spawnCz, spawnPreloadRadius);

            int px = (int) Math.floor(player.model.x);
            int pz = (int) Math.floor(player.model.z);
            int top = ChunkStreaming.topSolidY(world, px, pz);
            if (top >= 0) {
                player.placeAtWorld(player.model.x, top + 1.02f, player.model.z);
            } else {
                player.placeAtWorld(player.model.x, 80.0f, player.model.z);
            }

            Timer timer = new Timer();
            timer.init();
            boolean prevFire = false;
            boolean prevReload = false;
            boolean prevSwitch1 = false;
            boolean prevSwitch2 = false;
            boolean prevSwitch3 = false;
            boolean prevSwitch4 = false;
            boolean prevSlide = false;
            boolean sprintLatched = false;

            while (!glfwWindowShouldClose(handle)) {
                glfwPollEvents();

                if (Input.isKeyDown(GLFW_KEY_ESCAPE)) {
                    glfwSetWindowShouldClose(handle, true);
                }

                meshExecutor.drainToWorld(world);

                glClearColor(0.0f, 0.5f, 0.8f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                float dt = timer.getElapsedTime();
                if (dt > 0.1f) dt = 0.1f;
                timer.tickFrame(dt);

                boolean ads = Input.isMouseButtonDown(GLFW_MOUSE_BUTTON_RIGHT);
                boolean shiftHeld = Input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || Input.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
                boolean forwardHeld = Input.isKeyDown(GLFW_KEY_W);
                if (forwardHeld && shiftHeld) {
                    sprintLatched = true;
                } else if (!forwardHeld) {
                    sprintLatched = false;
                }
                boolean sprint = shiftHeld || sprintLatched;
                boolean slideNow = Input.isKeyDown(GLFW_KEY_LEFT_CONTROL) || Input.isKeyDown(GLFW_KEY_RIGHT_CONTROL);
                boolean slidePressed = slideNow && !prevSlide;
                boolean fireHeld = Input.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT);
                boolean firePressed = fireHeld && !prevFire;
                boolean reloadNow = Input.isKeyDown(GLFW_KEY_R);
                boolean reloadPressed = reloadNow && !prevReload;
                boolean switch1Now = Input.isKeyDown(GLFW_KEY_1);
                boolean switch2Now = Input.isKeyDown(GLFW_KEY_2);
                boolean switch3Now = Input.isKeyDown(GLFW_KEY_3);
                boolean switch4Now = Input.isKeyDown(GLFW_KEY_4);
                boolean switch1Pressed = switch1Now && !prevSwitch1;
                boolean switch2Pressed = switch2Now && !prevSwitch2;
                boolean switch3Pressed = switch3Now && !prevSwitch3;
                boolean switch4Pressed = switch4Now && !prevSwitch4;
                int wheelSteps = (int) Math.signum(Input.consumeScrollY());

                weaponSystem.update(dt, fireHeld, firePressed, ads, reloadPressed,
                        switch1Pressed, switch2Pressed, switch3Pressed, switch4Pressed, wheelSteps,
                        world, player.camera);
                for (WeaponSystem.ShotTrace t : weaponSystem.consumeShotTraces()) {
                    tracerRenderer.spawnTrace(t.sx(), t.sy(), t.sz(), t.ex(), t.ey(), t.ez());
                }
                tracerRenderer.update(dt);

                double dxMouse = Input.getMouseDx();
                double dyMouse = Input.getMouseDy();
                float sensitivity = 0.15f - weaponSystem.getAdsProgress() * 0.08f;
                player.camera.moveRotation((float) dyMouse * sensitivity, (float) dxMouse * sensitivity, 0);
                
                if (player.camera.getRotation().x > 89.0f) player.camera.getRotation().x = 89.0f;
                if (player.camera.getRotation().x < -89.0f) player.camera.getRotation().x = -89.0f;

                player.update(dt, world, 
                        Input.isKeyDown(GLFW_KEY_W), Input.isKeyDown(GLFW_KEY_S), 
                        Input.isKeyDown(GLFW_KEY_A), Input.isKeyDown(GLFW_KEY_D), 
                        Input.isKeyDown(GLFW_KEY_SPACE), sprint, weaponSystem.isAimingDownSights(),
                        slidePressed, slideNow);
                player.model.setWeaponAnimation(weaponSystem.getRecoilKick(),
                        weaponSystem.getReloadProgress(), weaponSystem.getAdsProgress());

                prevFire = fireHeld;
                prevReload = reloadNow;
                prevSwitch1 = switch1Now;
                prevSwitch2 = switch2Now;
                prevSwitch3 = switch3Now;
                prevSwitch4 = switch4Now;
                prevSlide = slideNow;

                int camChunkX = (int) Math.floor(player.camera.getPosition().x / Chunk.SIZE);
                int camChunkZ = (int) Math.floor(player.camera.getPosition().z / Chunk.SIZE);

                // Directional Chunk Loading Logic
                float yaw = (float) Math.toRadians(player.camera.getRotation().y);
                float dirX = -(float) Math.sin(yaw);
                float dirZ = -(float) Math.cos(yaw);

                record PendingChunk(int cx, int cz, int distSq) {}
                List<PendingChunk> pending = new ArrayList<>();
                for (int cx = camChunkX - viewDistance; cx <= camChunkX + viewDistance; cx++) {
                    for (int cz = camChunkZ - viewDistance; cz <= camChunkZ + viewDistance; cz++) {
                        float relX = cx - camChunkX;
                        float relZ = cz - camChunkZ;
                        float dist = (float) Math.sqrt(relX * relX + relZ * relZ);
                        float dot = (relX * dirX + relZ * dirZ) / (dist == 0 ? 1 : dist);
                        boolean shouldLoad = (dist <= defaultRadius) || (dist <= viewDistance && dot > loadForwardDotMin);
                        if (shouldLoad && world.getChunk(cx, cz) == null) {
                            int drx = cx - camChunkX;
                            int drz = cz - camChunkZ;
                            pending.add(new PendingChunk(cx, cz, drx * drx + drz * drz));
                        }
                    }
                }
                pending.sort(Comparator.comparingInt(PendingChunk::distSq));
                int chunksBuiltThisFrame = 0;
                for (PendingChunk p : pending) {
                    if (chunksBuiltThisFrame >= maxChunksToGeneratePerFrame) break;
                    if (world.getChunk(p.cx, p.cz) != null) continue;
                    Chunk chunk = new Chunk();
                    byte[] savedData = WorldPersistence.loadChunk(p.cx, p.cz);
                    if (savedData != null) {
                        chunk.setData(savedData);
                    } else {
                        worldGen.generate(chunk, p.cx, p.cz);
                    }
                    world.addChunk(p.cx, p.cz, chunk, null);
                    meshExecutor.submit(p.cx, p.cz, chunk);
                    chunksBuiltThisFrame++;
                }

                // Cleanup out of radius chunks
                List<int[]> toRemove = new ArrayList<>();
                for (World.ChunkData inst : world.getActiveChunks()) {
                    float relX = inst.cx - camChunkX;
                    float relZ = inst.cz - camChunkZ;
                    float dist = (float) Math.sqrt(relX*relX + relZ*relZ);
                    float dot = (relX * dirX + relZ * dirZ) / (dist == 0 ? 1 : dist);

                    // Slightly larger than load radii (hysteresis vs streaming).
                    boolean shouldKeep = (dist <= defaultRadius + 2) || (dist <= viewDistance + 2 && dot > 0.1f);
                    
                    if (!shouldKeep) {
                        toRemove.add(new int[]{inst.cx, inst.cz});
                    }
                }
                for (int[] pos : toRemove) {
                    world.removeChunk(pos[0], pos[1]);
                }

                meshExecutor.drainToWorld(world);

                skyRenderer.render(player.camera, window.getWidth(), window.getHeight(), (float) glfwGetTime());

                int solidAlbedoMode = world.getTotalSolidVoxels() >= RenderLod.TEXTURE_OFF_SOLID_VOXEL_THRESHOLD ? 1 : 0;
                renderer.beginRender(player.camera, window.getWidth(), window.getHeight(), solidAlbedoMode,
                        weaponSystem.getCurrentFovDegrees());
                for (World.ChunkData instance : world.getActiveChunks()) {
                    int dcx = Math.abs(instance.cx - camChunkX);
                    int dcz = Math.abs(instance.cz - camChunkZ);
                    if (Math.max(dcx, dcz) > renderChebyshevChunks) {
                        continue;
                    }
                    if (instance.mesh == null) {
                        continue;
                    }
                    float minX = instance.cx * Chunk.SIZE;
                    float minZ = instance.cz * Chunk.SIZE;
                    float maxX = minX + Chunk.SIZE;
                    float maxZ = minZ + Chunk.SIZE;
                    if (!renderer.isAabbInViewFrustum(minX, 0, minZ, maxX, Chunk.SIZE, maxZ)) {
                        continue;
                    }
                    renderer.renderMesh(instance.mesh, minX, 0, minZ);
                }
                player.model.render(renderer);
                tracerRenderer.render(renderer);
                renderer.endRender();

                fpsOverlay.render(timer.getFPS(), window.getWidth(), window.getHeight());
                weaponOverlay.render(weaponSystem.getCurrentWeaponName(), weaponSystem.getAmmoInMag(),
                        weaponSystem.getReserveAmmo(), weaponSystem.getAdsProgress(), weaponSystem.getReloadProgress(),
                        window.getWidth(), window.getHeight());

                glfwSwapBuffers(handle);
            }

            if (meshExecutor != null) {
                meshExecutor.close();
                meshExecutor = null;
            }

            for (World.ChunkData inst : world.getActiveChunks()) {
                if (inst.mesh == null && inst.chunk != null) {
                    inst.mesh = ChunkMesher.buildMesh(inst.chunk);
                }
            }
            // Cleanup remaining chunks (Save process)
            for (World.ChunkData inst : world.getActiveChunks()) {
                WorldPersistence.saveChunk(inst.cx, inst.cz, inst.chunk.getData());
                if (inst.mesh != null) inst.mesh.cleanup();
            }
            renderer.cleanup();
            skyRenderer.cleanup();
            fpsOverlay.cleanup();
            weaponOverlay.cleanup();
            tracerRenderer.cleanup();
            WorldPersistence.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (mainMenu != null) {
                mainMenu.cleanup();
            }
            if (meshExecutor != null) {
                meshExecutor.close();
            }
        }

        window.destroy();
    }
}
