package entity;

import org.joml.Matrix4f;
import render.BoxMeshBuilder;
import render.Mesh;
import render.Renderer;

public class CharacterModel {
    private Mesh head, torso, armL, armR, legL, legR;
    public float x, y, z;
    public float time;
    public float rotationY, rotationX;
    public boolean firstPerson = true;

    public CharacterModel() {
        head = BoxMeshBuilder.createBox(4, 4, 4, 0.95f, 0.8f, 0.7f); // Piel
        torso = BoxMeshBuilder.createBox(5.0f, 6.0f, 3f, 0.2f, 0.5f, 0.8f); // Camisa
        armL = BoxMeshBuilder.createBox(2f, 6.0f, 2f, 0.2f, 0.5f, 0.8f);  
        armR = BoxMeshBuilder.createBox(2f, 6.0f, 2f, 0.2f, 0.5f, 0.8f);
        legL = BoxMeshBuilder.createBox(2.2f, 6.0f, 2.5f, 0.1f, 0.1f, 0.4f); // Pantalon
        legR = BoxMeshBuilder.createBox(2.2f, 6.0f, 2.5f, 0.1f, 0.1f, 0.4f);
    }

    public void update(float dt, boolean isMoving) {
        if (isMoving) time += dt * 10;
        else time += dt * 1.5f;
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
        }
    }
}
