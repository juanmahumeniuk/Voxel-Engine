package entity;

import org.joml.Matrix4f;
import render.BoxMeshBuilder;
import render.Mesh;
import render.Renderer;

public class CharacterModel {
    private Mesh head, torso, armL, armR, legL, legR;
    private Mesh deagleSlide, deagleFrame, deagleGrip, deagleBarrel, deagleSight;
    public float x, y, z;
    public float time;
    public float rotationY, rotationX;
    public boolean firstPerson = true;
    private float weaponRecoil = 0f;
    private float reloadProgress = 0f;
    private float adsProgress = 0f;
    private float slideAmount = 0f;

    public CharacterModel() {
        head = BoxMeshBuilder.createBox(4, 4, 4, 0.95f, 0.8f, 0.7f); // Piel
        torso = BoxMeshBuilder.createBox(5.0f, 6.0f, 3f, 0.2f, 0.5f, 0.8f); // Camisa
        armL = BoxMeshBuilder.createBox(2f, 6.0f, 2f, 0.2f, 0.5f, 0.8f);  
        armR = BoxMeshBuilder.createBox(2f, 6.0f, 2f, 0.2f, 0.5f, 0.8f);
        legL = BoxMeshBuilder.createBox(2.2f, 6.0f, 2.5f, 0.1f, 0.1f, 0.4f); // Pantalon
        legR = BoxMeshBuilder.createBox(2.2f, 6.0f, 2.5f, 0.1f, 0.1f, 0.4f);
        deagleSlide = BoxMeshBuilder.createBox(4.8f, 1.1f, 1.1f, 0.10f, 0.10f, 0.12f);
        deagleFrame = BoxMeshBuilder.createBox(4.2f, 1.0f, 1.2f, 0.15f, 0.15f, 0.18f);
        deagleGrip = BoxMeshBuilder.createBox(1.1f, 2.2f, 1.0f, 0.07f, 0.07f, 0.08f);
        deagleBarrel = BoxMeshBuilder.createBox(1.8f, 0.55f, 0.65f, 0.08f, 0.08f, 0.09f);
        deagleSight = BoxMeshBuilder.createBox(0.45f, 0.25f, 0.25f, 0.23f, 0.23f, 0.25f);
    }

    public void update(float dt, boolean isMoving) {
        if (isMoving) time += dt * 10;
        else time += dt * 1.5f;
    }

    public void setWeaponAnimation(float recoil, float reloadProgress, float adsProgress) {
        this.weaponRecoil = recoil;
        this.reloadProgress = reloadProgress;
        this.adsProgress = adsProgress;
    }

    public void setSlideAmount(float slideAmount) {
        this.slideAmount = Math.max(0f, Math.min(1f, slideAmount));
    }

    public void render(Renderer renderer) {
        Matrix4f base = new Matrix4f().translate(x, y, z).rotateY(rotationY);
        Matrix4f m = new Matrix4f();
        
        float localTorsoY = 7f + (float)Math.sin(time * 0.5f) * 0.5f;
        
        if (!firstPerson) {
            // Torso
            m.set(base).translate(0, localTorsoY, 0);
            renderer.renderMesh(torso, m);

            // Cabeza (Asume la rotacion del pitch también)
            m.set(base).translate(0, localTorsoY + 4.5f, 0).rotateX(rotationX);
            renderer.renderMesh(head, m);

            // Brazo Izquierdo (Mira hacia adelante si la cabeza mira hacia arriba ligeramente)
            m.set(base)
             .translate(3.6f, localTorsoY + 2.5f, 0)
             .rotateX((float)Math.sin(time) * 0.6f + rotationX * 0.2f)
             .translate(0, -3.0f, 0);
            renderer.renderMesh(armL, m);

            // Brazo Derecho
            m.set(base)
             .translate(-3.6f, localTorsoY + 2.5f, 0)
             .rotateX((float)-Math.sin(time) * 0.6f + rotationX * 0.2f)
             .translate(0, -3.0f, 0);
            renderer.renderMesh(armR, m);

            // Pierna Izquierda
            m.set(base).translate(1.2f, localTorsoY - 3f, 0).rotateX((float)-Math.sin(time) * 0.6f).translate(0, -3f, 0);
            renderer.renderMesh(legL, m);

            // Pierna Derecha
            m.set(base).translate(-1.2f, localTorsoY - 3f, 0).rotateX((float)Math.sin(time) * 0.6f).translate(0, -3f, 0);
            renderer.renderMesh(legR, m);
            
        } else {
            // Primera Persona
            // Cuando la vista es primera persona, ocultamos todo salvo los brazos frente a la pantalla
            float camHeight = localTorsoY + 4f; 
            
            // Brazo Izquierdo
            m.set(base)
             .translate(0, camHeight, 0)
             .rotateX(rotationX) // Alinear con mira arriba/abajo
             .translate(3.5f, -4.0f, -2.0f) // Desplazado MUCHO más abajo y a la esquina izquierda
             .rotateX((float)Math.sin(time) * 0.1f - 1.5f) // Menos invasivo, más acostado
             .rotateZ(0.4f);
            renderer.renderMesh(armL, m);

            // Brazo Derecho
            m.set(base)
             .translate(0, camHeight, 0)
             .rotateX(rotationX) 
             .translate(-3.5f, -4.0f, -2.0f) // Esquina inferior derecha
             .rotateX((float)-Math.sin(time) * 0.1f - 1.5f)
             .rotateZ(-0.4f);
            renderer.renderMesh(armR, m);

            if (slideAmount > 0.01f) {
                // Slide first-person pose: feet ahead, Fortnite-like.
                Matrix4f legBase = new Matrix4f(base)
                        .translate(0, camHeight, 0)
                        .rotateX(rotationX)
                        .translate(0, -7.6f + slideAmount * 0.2f, -2.4f - slideAmount * 2.8f)
                        .rotateX((float) Math.toRadians(-76.0 + slideAmount * 8.0));

                m.set(legBase).translate(1.0f, 0.2f, 0.2f).rotateZ(0.05f);
                renderer.renderMesh(legL, m);

                m.set(legBase).translate(-1.0f, 0.2f, 0.2f).rotateZ(-0.05f);
                renderer.renderMesh(legR, m);
            }

            // Modelo Deagle en mano derecha (primera persona)
            Matrix4f gunBase = new Matrix4f(base)
                    .translate(0, camHeight, 0)
                    .rotateX(rotationX)
                    .translate(-2.2f + adsProgress * 2.10f, -3.6f + adsProgress * 1.35f, -4.4f - adsProgress * 2.85f)
                    .translate(0.0f, -reloadProgress * 2.2f, weaponRecoil * (1.35f - 0.65f * adsProgress))
                    .rotateX(-weaponRecoil * 0.25f)
                    .rotateZ(reloadProgress * 1.05f)
                    // Gun meshes are authored with barrel along +X; rotate to face camera forward (-Z).
                    .rotateY((float) Math.toRadians(90.0f - adsProgress * 1.5f))
                    .rotateZ(-0.18f * (1.0f - adsProgress));
            float kick = (float) Math.sin(time * 7.5f) * 0.02f + weaponRecoil * 0.45f;

            m.set(gunBase).translate(0.55f, 0.40f + kick, 0.0f);
            renderer.renderMesh(deagleSlide, m);

            m.set(gunBase).translate(0.05f, -0.1f + kick, 0.0f);
            renderer.renderMesh(deagleFrame, m);

            m.set(gunBase).translate(-1.15f, -1.2f + kick, 0.0f).rotateZ(0.35f);
            renderer.renderMesh(deagleGrip, m);

            m.set(gunBase).translate(2.5f, 0.35f + kick, 0.0f);
            renderer.renderMesh(deagleBarrel, m);

            m.set(gunBase).translate(1.35f, 1.0f + kick, 0.0f);
            renderer.renderMesh(deagleSight, m);
        }
    }
}
