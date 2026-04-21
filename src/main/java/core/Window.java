package core;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {
    private int width, height;
    private String title;
    private long glfwWindow;
    private static Window window = null;

    private Window() {
        this.width = 1280;
        this.height = 720;
        this.title = "Voxel Engine (High Density)";
    }

    public static Window get() {
        if (Window.window == null) {
            Window.window = new Window();
        }
        return Window.window;
    }

    public void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);

        glfwWindow = glfwCreateWindow(this.width, this.height, this.title, NULL, NULL);
        if (glfwWindow == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Setup input callbacks
        glfwSetKeyCallback(glfwWindow, Input::keyCallback);
        glfwSetMouseButtonCallback(glfwWindow, Input::mouseButtonCallback);
        glfwSetCursorPosCallback(glfwWindow, Input::mouseCursorPosCallback);

        glfwSetFramebufferSizeCallback(glfwWindow, (window, w, h) -> {
            this.width = w;
            this.height = h;
            glViewport(0, 0, w, h);
        });

        glfwMakeContextCurrent(glfwWindow);
        GL.createCapabilities();
        // 0 = uncapped frame rate (helps reach 60+ when GPU-bound); use 1 for V-Sync / tear-free cap.
        glfwSwapInterval(0);
        glfwShowWindow(glfwWindow);
        glfwMaximizeWindow(glfwWindow);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            glfwGetFramebufferSize(glfwWindow, w, h);
            this.width = w.get(0);
            this.height = h.get(0);
            glViewport(0, 0, this.width, this.height);
        }

        glEnable(GL_DEPTH_TEST);
    }

    public long getHandle() { return glfwWindow; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void destroy() {
        glfwFreeCallbacks(glfwWindow);
        glfwDestroyWindow(glfwWindow);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
}
