package entity;

import math.Camera;
import org.joml.Vector3f;
import world.World;

public class Player {
    private static final float WALK_SPEED = 25.0f;
    private static final float SPRINT_MULT = 2.10f;
    private static final float ADS_MULT = 0.65f;
    private static final float SLIDE_MAX_TIME = 1.00f;
    private static final float SLIDE_MIN_TIME = 0.28f;
    private static final float SLIDE_FRICTION_PER_SEC = 36.0f;
    private static final float SLIDE_START_BOOST = 13.0f;

    public Camera camera;
    public CharacterModel model;
    
    public Vector3f velocity;
    public boolean isGrounded = false;
    private float smoothedCameraY = -1f; // Initial flag
    private boolean sliding = false;
    private float slideTimer = 0f;
    private float slideVx = 0f;
    private float slideVz = 0f;
    private float slideSpeed = 0f;

    // Hitbox dimensions based on HD blocks scaling
    public float width = 2.8f; 
    public float height = 11.5f;
    public float depth = 2.8f;

    public Player() {
        camera = new Camera();
        model = new CharacterModel();
        velocity = new Vector3f();
    }

    /** Snap player and camera after spawn preload (resets vertical smoothing). */
    public void placeAtWorld(float x, float y, float z) {
        model.x = x;
        model.y = y;
        model.z = z;
        velocity.set(0, 0, 0);
        isGrounded = true;
        smoothedCameraY = -1f;
        camera.getPosition().x = x;
        camera.getPosition().y = y + 11.0f;
        camera.getPosition().z = z;
    }

    public void update(float dt, World world, boolean moveF, boolean moveB, boolean moveL, boolean moveR,
                       boolean jump, boolean sprint, boolean ads, boolean slidePressed, boolean slideHeld) {
        float speed = WALK_SPEED;
        if (sprint && !ads) speed *= SPRINT_MULT;
        if (ads) speed *= ADS_MULT;
        
        float dx = 0, dz = 0;
        if (moveF) dz -= 1;
        if (moveB) dz += 1;
        if (moveL) dx -= 1;
        if (moveR) dx += 1;
        boolean hasMoveInput = dx != 0 || dz != 0;

        if (hasMoveInput) {
            float len = (float)Math.sqrt(dx*dx + dz*dz);
            dx /= len; 
            dz /= len;
            
            float yaw = (float)Math.toRadians(camera.getRotation().y);
            float rotatedX = dx * (float)Math.cos(yaw) - dz * (float)Math.sin(yaw);
            float rotatedZ = dx * (float)Math.sin(yaw) + dz * (float)Math.cos(yaw);
            
            if (!sliding) {
                velocity.x = rotatedX * speed;
                velocity.z = rotatedZ * speed;
            }
        } else {
            if (!sliding) {
                velocity.x *= 0.75f;
                velocity.z *= 0.75f;
            }
        }

        if (slidePressed && !sliding && isGrounded && sprint && !ads) {
            float dirX = velocity.x;
            float dirZ = velocity.z;
            float dirLen = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (dirLen > 0.001f) {
                slideVx = dirX / dirLen;
                slideVz = dirZ / dirLen;
                slideSpeed = Math.max(speed + SLIDE_START_BOOST, WALK_SPEED * 2.2f);
                sliding = true;
                slideTimer = 0f;
            }
        }

        if (sliding) {
            slideTimer += dt;
            slideSpeed = Math.max(0f, slideSpeed - SLIDE_FRICTION_PER_SEC * dt);
            velocity.x = slideVx * slideSpeed;
            velocity.z = slideVz * slideSpeed;
            if (slideTimer >= SLIDE_MAX_TIME
                    || slideSpeed < WALK_SPEED
                    || (!slideHeld && slideTimer >= SLIDE_MIN_TIME)
                    || !isGrounded) {
                sliding = false;
            }
        }

        // Gravity pulling down
        velocity.y -= 75.0f * dt;

        if (jump && isGrounded) {
            velocity.y = 35.0f; 
            isGrounded = false;
        }

        move(velocity.x * dt, velocity.y * dt, velocity.z * dt, world);

        // Keep sprint cadence while stepping over blocks: animation follows movement intent.
        float horizontalSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
        boolean isAnimatingMovement = sliding || (hasMoveInput && (sprint || horizontalSpeedSq > 4.0f));
        model.update(dt, isAnimatingMovement);

        // Smoothing Camera Y (Step smoothing)
        float slideVisual = sliding ? Math.min(1.0f, slideTimer / 0.12f) : 0.0f;
        float targetCameraY = this.model.y + 11.0f - slideVisual * 2.4f;
        if (smoothedCameraY < 0) smoothedCameraY = targetCameraY;
        
        // Lerp factor (higher = faster response)
        float lerpFactor = 15.0f;
        smoothedCameraY += (targetCameraY - smoothedCameraY) * Math.min(dt * lerpFactor, 1.0f);

        camera.getPosition().y = smoothedCameraY;
        camera.getPosition().x = this.model.x;
        camera.getPosition().z = this.model.z;
        
        model.rotationY = (float) Math.toRadians(-camera.getRotation().y);
        model.rotationX = (float) Math.toRadians(-camera.getRotation().x);
        model.setSlideAmount(slideVisual);
    }

    private void move(float dx, float dy, float dz, World world) {
        float stepHeight = 2.05f; // Slightly more than 2 to handle rounding

        // 1. Vertical Movement (Gravity / Jump)
        if (dy != 0) {
            if (!checkCollision(model.x, model.y + dy, model.z, world)) {
                model.y += dy;
                isGrounded = false;
            } else {
                if (dy < 0) isGrounded = true; 
                velocity.y = 0;
            }
        }

        // 2. Horizontal Movement (X and Z) with fluid Step-Up
        float oldX = model.x;
        float oldY = model.y;
        float oldZ = model.z;

        // Try direct move first
        boolean collisionX = checkCollision(model.x + dx, model.y, model.z, world);
        if (!collisionX) {
            model.x += dx;
        } else if (isGrounded) {
            // Try Step-Up X
            float stepUp = findStepUpOffset(model.x + dx, model.y, model.z, world, stepHeight);
            if (stepUp >= 0f) {
                model.y += stepUp;
                model.x += dx;
            } else {
                velocity.x = 0;
            }
        }

        boolean collisionZ = checkCollision(model.x, model.y, model.z + dz, world);
        if (!collisionZ) {
            model.z += dz;
        } else if (isGrounded) {
            // Try Step-Up Z
            float stepUp = findStepUpOffset(model.x, model.y, model.z + dz, world, stepHeight);
            if (stepUp >= 0f) {
                model.y += stepUp;
                model.z += dz;
            } else {
                velocity.z = 0;
            }
        }

        // 3. Forced Grounding check (prevent floating after step-up)
        // If we stepped up, we might be slightly in the air. 
        // We don't snap down here to keep it fluid; gravity will handle it.
    }

    /**
     * Returns the minimal positive Y offset that clears collision for the target XZ, or -1 if impossible.
     * Using the smallest valid offset avoids abrupt "teleport up" when climbing short steps.
     */
    private float findStepUpOffset(float targetX, float baseY, float targetZ, World world, float maxStepHeight) {
        float stepIncrement = 0.25f;
        for (float up = stepIncrement; up <= maxStepHeight + 0.0001f; up += stepIncrement) {
            if (!checkCollision(targetX, baseY + up, targetZ, world)) {
                return up;
            }
        }
        return -1f;
    }

    private boolean checkCollision(float px, float py, float pz, World world) {
        float margin = 0.05f; // Resolve wall scraping rounding errors
        int minX = (int)Math.floor(px - width/2 + margin);
        int maxX = (int)Math.floor(px + width/2 - margin);
        int minY = (int)Math.floor(py);
        int maxY = (int)Math.floor(py + height - margin);
        int minZ = (int)Math.floor(pz - depth/2 + margin);
        int maxZ = (int)Math.floor(pz + depth/2 - margin);

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (world.getVoxel(x, y, z) != 0) return true;
                }
            }
        }
        return false;
    }
}
