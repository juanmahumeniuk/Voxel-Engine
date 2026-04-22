package entity;

import core.GameAudio;
import math.Camera;
import world.World;

import java.util.ArrayList;
import java.util.List;

public class WeaponSystem {
    public record ShotTrace(float sx, float sy, float sz, float ex, float ey, float ez) {}

    private record WeaponDef(String name, int magSize, int reserveAmmo, float fireIntervalSec, float adsFov, float range,
                             boolean automatic, int pellets, float reloadSec, float shotBrightness) {}

    private static final WeaponDef[] WEAPONS = {
            new WeaponDef("DEAGLE", 7, 56, 0.210f, 34.0f, 230.0f, false, 1, 1.70f, 0.70f),
            new WeaponDef("AK-47", 30, 180, 0.095f, 42.0f, 230.0f, true, 1, 2.20f, 0.90f),
            new WeaponDef("MP5", 42, 210, 0.060f, 48.0f, 170.0f, true, 1, 2.05f, 1.00f),
            new WeaponDef("AX-50", 5, 35, 1.050f, 26.0f, 300.0f, false, 1, 2.80f, 0.50f)
    };

    private final int[] ammoInMag = new int[WEAPONS.length];
    private final int[] reserveAmmo = new int[WEAPONS.length];
    private final List<ShotTrace> pendingTraces = new ArrayList<>();
    private int selected = 0;
    private float cooldown = 0.0f;
    private float adsProgress = 0.0f;
    private boolean adsHeld = false;
    private float recoil = 0.0f;
    private float reloadTimer = 0.0f;
    private float reloadDuration = 0.0f;
    private boolean reloading = false;

    public WeaponSystem() {
        for (int i = 0; i < WEAPONS.length; i++) {
            ammoInMag[i] = WEAPONS[i].magSize();
            reserveAmmo[i] = WEAPONS[i].reserveAmmo();
        }
    }

    public void update(float dt, boolean fireHeld, boolean firePressed, boolean adsHeld, boolean reloadPressed,
                       boolean switch1, boolean switch2, boolean switch3, boolean switch4, int wheelSteps,
                       World world, Camera camera) {
        if (cooldown > 0) cooldown -= dt;
        recoil = Math.max(0.0f, recoil - dt * 5.2f);
        if (switch1) switchWeapon(0);
        if (switch2) switchWeapon(1);
        if (switch3) switchWeapon(2);
        if (switch4) switchWeapon(3);
        if (wheelSteps != 0) switchWeaponRelative(wheelSteps > 0 ? -1 : 1);

        WeaponDef w = WEAPONS[selected];
        this.adsHeld = adsHeld && !reloading;
        float adsTarget = this.adsHeld ? 1.0f : 0.0f;
        float adsSpeed = 8.0f;
        if (adsProgress < adsTarget) adsProgress = Math.min(adsTarget, adsProgress + dt * adsSpeed);
        else adsProgress = Math.max(adsTarget, adsProgress - dt * adsSpeed);

        if (reloading) {
            reloadTimer += dt;
            if (reloadTimer >= reloadDuration) {
                finishReload();
            }
        }

        if (reloadPressed) {
            startReload();
        }

        if (!reloading) {
            boolean wantsShot = w.automatic() ? fireHeld : firePressed;
            if (wantsShot && cooldown <= 0.0f && ammoInMag[selected] > 0) {
                fire(world, camera, w);
                ammoInMag[selected]--;
                cooldown = w.fireIntervalSec();
                recoil = Math.min(1.0f, recoil + 0.38f);
                GameAudio.playShot(w.shotBrightness());
                if (ammoInMag[selected] <= 0 && reserveAmmo[selected] > 0) {
                    startReload();
                }
            }
        }
    }

    private void switchWeapon(int index) {
        if (index < 0 || index >= WEAPONS.length || selected == index) return;
        selected = index;
        reloading = false;
        reloadTimer = 0.0f;
        reloadDuration = 0.0f;
    }

    private void switchWeaponRelative(int delta) {
        int len = WEAPONS.length;
        int next = Math.floorMod(selected + delta, len);
        switchWeapon(next);
    }

    private void startReload() {
        if (reloading) return;
        WeaponDef w = WEAPONS[selected];
        int missing = w.magSize() - ammoInMag[selected];
        if (missing <= 0 || reserveAmmo[selected] <= 0) return;
        reloading = true;
        reloadTimer = 0.0f;
        reloadDuration = w.reloadSec();
        cooldown = Math.max(cooldown, 0.12f);
        GameAudio.playReload();
    }

    private void finishReload() {
        WeaponDef w = WEAPONS[selected];
        int missing = w.magSize() - ammoInMag[selected];
        int moved = Math.min(missing, reserveAmmo[selected]);
        ammoInMag[selected] += moved;
        reserveAmmo[selected] -= moved;
        reloading = false;
        reloadTimer = 0.0f;
        reloadDuration = 0.0f;
    }

    private void fire(World world, Camera camera, WeaponDef w) {
        for (int p = 0; p < w.pellets(); p++) {
            castHitscan(world, camera, w.range());
        }
    }

    private void castHitscan(World world, Camera camera, float range) {
        float pitch = (float) Math.toRadians(camera.getRotation().x);
        float yaw = (float) Math.toRadians(camera.getRotation().y);
        float fx = (float) Math.sin(yaw) * (float) Math.cos(pitch);
        float fy = -(float) Math.sin(pitch);
        float fz = -(float) Math.cos(yaw) * (float) Math.cos(pitch);
        float dirX = fx;
        float dirY = fy;
        float dirZ = fz;
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len <= 0.0001f) return;
        dirX /= len;
        dirY /= len;
        dirZ /= len;

        float step = 0.45f;
        float ox = camera.getPosition().x;
        float oy = camera.getPosition().y;
        float oz = camera.getPosition().z;
        float endX = ox + dirX * range;
        float endY = oy + dirY * range;
        float endZ = oz + dirZ * range;
        for (float t = 0.0f; t <= range; t += step) {
            int x = (int) Math.floor(ox + dirX * t);
            int y = (int) Math.floor(oy + dirY * t);
            int z = (int) Math.floor(oz + dirZ * t);
            if (world.getVoxel(x, y, z) != 0) {
                world.setVoxelAndRebuild(x, y, z, (byte) 0);
                endX = x + 0.5f;
                endY = y + 0.5f;
                endZ = z + 0.5f;
                break;
            }
        }
        pendingTraces.add(new ShotTrace(ox, oy, oz, endX, endY, endZ));
    }

    public boolean isAimingDownSights() {
        return adsProgress > 0.4f;
    }

    public float getCurrentFovDegrees() {
        float hip = 60.0f;
        float ads = WEAPONS[selected].adsFov();
        return hip + (ads - hip) * adsProgress;
    }

    public String getCurrentWeaponName() {
        return WEAPONS[selected].name();
    }

    public int getAmmoInMag() {
        return ammoInMag[selected];
    }

    public int getReserveAmmo() {
        return reserveAmmo[selected];
    }

    public float getAdsProgress() {
        return adsProgress;
    }

    public float getReloadProgress() {
        if (!reloading || reloadDuration <= 0.0001f) return 0.0f;
        return Math.min(1.0f, reloadTimer / reloadDuration);
    }

    public float getRecoilKick() {
        return recoil;
    }

    public List<ShotTrace> consumeShotTraces() {
        if (pendingTraces.isEmpty()) return List.of();
        List<ShotTrace> out = new ArrayList<>(pendingTraces);
        pendingTraces.clear();
        return out;
    }
}
