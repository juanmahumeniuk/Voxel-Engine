package core;

import org.lwjgl.glfw.GLFW;

public class Input {
    private static boolean[] keys = new boolean[GLFW.GLFW_KEY_LAST];
    private static boolean[] mouseButtons = new boolean[GLFW.GLFW_MOUSE_BUTTON_LAST];
    private static double mouseX, mouseY;
    private static double mouseDx, mouseDy;
    private static boolean firstMouse = true;

    public static void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (key >= 0 && key < GLFW.GLFW_KEY_LAST) {
            keys[key] = (action != GLFW.GLFW_RELEASE);
        }
    }

    public static void mouseButtonCallback(long window, int button, int action, int mods) {
        if (button >= 0 && button < GLFW.GLFW_MOUSE_BUTTON_LAST) {
            mouseButtons[button] = (action != GLFW.GLFW_RELEASE);
        }
    }

    public static void mouseCursorPosCallback(long window, double xpos, double ypos) {
        if (firstMouse) {
            mouseX = xpos;
            mouseY = ypos;
            firstMouse = false;
        }
        mouseDx = xpos - mouseX;
        mouseDy = ypos - mouseY;
        mouseX = xpos;
        mouseY = ypos;
    }

    public static boolean isKeyDown(int key) {
        return keys[key];
    }

    public static boolean isMouseButtonDown(int button) {
        return mouseButtons[button];
    }

    public static double getMouseX() { return mouseX; }
    public static double getMouseY() { return mouseY; }

    public static double getMouseDx() { 
        double dx = mouseDx; 
        mouseDx = 0; 
        return dx; 
    }

    public static double getMouseDy() { 
        double dy = mouseDy; 
        mouseDy = 0; 
        return dy; 
    }
}
