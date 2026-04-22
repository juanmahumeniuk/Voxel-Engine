package core;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public final class GameAudio {
    private static final AudioFormat PCM_16_MONO_44K = new AudioFormat(44100f, 16, 1, true, false);

    private GameAudio() {
    }

    public static void playShot(float brightness) {
        playAsync(() -> {
            int sampleRate = (int) PCM_16_MONO_44K.getSampleRate();
            int samples = (int) (sampleRate * 0.14f);
            byte[] pcm = new byte[samples * 2];
            double seed = System.nanoTime() * 0.000000001;
            for (int i = 0; i < samples; i++) {
                float t = i / (float) sampleRate;
                float env = (float) Math.exp(-t * 26.0f);
                float freq = 120.0f + brightness * 110.0f;
                float tone = (float) Math.sin(2.0 * Math.PI * freq * t) * 0.50f;
                float crack = (float) (Math.sin((t + seed) * 9000.0f) * Math.cos((t + seed) * 7000.0f)) * 0.45f;
                float value = (tone + crack) * env;
                short s = (short) (Math.max(-1.0f, Math.min(1.0f, value)) * 22000);
                pcm[i * 2] = (byte) (s & 0xff);
                pcm[i * 2 + 1] = (byte) ((s >>> 8) & 0xff);
            }
            writePcm(pcm);
        });
    }

    public static void playReload() {
        playAsync(() -> {
            int sampleRate = (int) PCM_16_MONO_44K.getSampleRate();
            int samples = (int) (sampleRate * 0.22f);
            byte[] pcm = new byte[samples * 2];
            for (int i = 0; i < samples; i++) {
                float t = i / (float) sampleRate;
                float env = (float) Math.exp(-t * 14.0f);
                float c1 = (float) Math.sin(2.0 * Math.PI * 420.0f * t);
                float c2 = (float) Math.sin(2.0 * Math.PI * 690.0f * t);
                float value = (0.55f * c1 + 0.45f * c2) * env;
                short s = (short) (Math.max(-1.0f, Math.min(1.0f, value)) * 17000);
                pcm[i * 2] = (byte) (s & 0xff);
                pcm[i * 2 + 1] = (byte) ((s >>> 8) & 0xff);
            }
            writePcm(pcm);
        });
    }

    private static void writePcm(byte[] pcm) {
        try (SourceDataLine line = AudioSystem.getSourceDataLine(PCM_16_MONO_44K)) {
            line.open(PCM_16_MONO_44K);
            line.start();
            line.write(pcm, 0, pcm.length);
            line.drain();
        } catch (Exception ignored) {
            // Keep gameplay resilient when audio device is unavailable.
        }
    }

    private static void playAsync(Runnable task) {
        Thread t = new Thread(task, "voxel-audio");
        t.setDaemon(true);
        t.start();
    }
}
