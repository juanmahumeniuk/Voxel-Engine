package core;

import org.lwjgl.glfw.GLFW;

public class Timer {
    private double lastLoopTime;
    private float timeCount;
    private int fps;
    private int fpsCount;

    public void init() {
        lastLoopTime = getTime();
    }

    public double getTime() {
        return GLFW.glfwGetTime();
    }

    public float getElapsedTime() {
        double time = getTime();
        float elapsedTime = (float) (time - lastLoopTime);
        lastLoopTime = time;
        return elapsedTime;
    }

    public void updateFPS() {
        fpsCount++;
    }

    public void updateTimer() {
        timeCount += getElapsedTime();
        if (timeCount > 1f) {
            fps = fpsCount;
            fpsCount = 0;
            timeCount -= 1f;
        }
    }

    public int getFPS() {
        return fps;
    }
}
