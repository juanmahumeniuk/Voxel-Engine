package entity;

import math.Camera;
import org.joml.Vector3f;
import world.World;

public class Player {
    public Camera camera;
    public CharacterModel model;
    
    public Vector3f velocity;
    public boolean isGrounded = false;
    private float smoothedCameraY = -1f; // Initial flag

    // Hitbox dimensions based on HD blocks scaling
    public float width = 2.8f; 
    public float height = 11.5f;
    public float depth = 2.8f;

    public Player() {
        camera = new Camera();
        model = new CharacterModel();
        velocity = new Vector3f();
    }

    public void update(float dt, World world, boolean moveF, boolean moveB, boolean moveL, boolean moveR, boolean jump) {
        float speed = 25.0f; // Blocks (voxels) per second
        
        float dx = 0, dz = 0;
        if (moveF) dz -= 1;
        if (moveB) dz += 1;
        if (moveL) dx -= 1;
        if (moveR) dx += 1;

        if (dx != 0 || dz != 0) {
            float len = (float)Math.sqrt(dx*dx + dz*dz);
            dx /= len; 
            dz /= len;
            
            float yaw = (float)Math.toRadians(camera.getRotation().y);
            float rotatedX = dx * (float)Math.cos(yaw) - dz * (float)Math.sin(yaw);
            float rotatedZ = dx * (float)Math.sin(yaw) + dz * (float)Math.cos(yaw);
            
            velocity.x = rotatedX * speed;
            velocity.z = rotatedZ * speed;
            model.update(dt, true);
        } else {
            velocity.x *= 0.75f; 
            velocity.z *= 0.75f;
            model.update(dt, false);
        }

        // Gravity pulling down
        velocity.y -= 75.0f * dt;

        if (jump && isGrounded) {
            velocity.y = 35.0f; 
            isGrounded = false;
        }

        move(velocity.x * dt, velocity.y * dt, velocity.z * dt, world);

        // Smoothing Camera Y (Step smoothing)
        float targetCameraY = this.model.y + 11.0f;
        if (smoothedCameraY < 0) smoothedCameraY = targetCameraY;
        
        // Lerp factor (higher = faster response)
        float lerpFactor = 15.0f;
        smoothedCameraY += (targetCameraY - smoothedCameraY) * Math.min(dt * lerpFactor, 1.0f);

        camera.getPosition().y = smoothedCameraY;
        camera.getPosition().x = this.model.x;
        camera.getPosition().z = this.model.z;
        
        model.rotationY = (float) Math.toRadians(-camera.getRotation().y);
        model.rotationX = (float) Math.toRadians(-camera.getRotation().x);
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
            if (!checkCollision(model.x + dx, model.y + stepHeight, model.z, world)) {
                model.y += stepHeight;
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
            if (!checkCollision(model.x, model.y + stepHeight, model.z + dz, world)) {
                model.y += stepHeight;
                model.z += dz;
            } else {
                velocity.z = 0;
            }
        }

        // 3. Forced Grounding check (prevent floating after step-up)
        // If we stepped up, we might be slightly in the air. 
        // We don't snap down here to keep it fluid; gravity will handle it.
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
