import com.sun.media.sound.AudioSynthesizer;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * MIDI -> WAV 离线渲染（JDK Gervill 软波表）。
 * 运行（JDK21 需开放内部 API）：
 *   java --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED -cp buildtmp MidiRender res web
 * 输出 web/audio.js: window.__audio = {main: dataURL, bg: dataURL}
 * 循环曲做尾静音裁剪保证循环无缝。
 */
public class MidiRender {
    public static void main(String[] args) throws Exception {
        String resDir = args.length > 0 ? args[0] : "res";
        String webDir = args.length > 1 ? args[1] : "web";
        float sampleRate = args.length > 2 ? Float.parseFloat(args[2]) : 16000f;

        StringBuilder js = new StringBuilder("window.__audio = {\n");
        String[] names = {"main", "bg"};
        int[] caps = {45, 60}; // 每曲最大渲染秒数（main.mid 是长曲，截断循环播放）
        for (int i = 0; i < names.length; i++) {
            File mid = new File(resDir, (i == 0 ? "main.mid" : "bg.mid"));
            System.out.println("rendering " + mid.getPath() + " (cap " + caps[i] + "s) ...");
            byte[] wav = render(mid, sampleRate, caps[i]);
            String b64 = java.util.Base64.getEncoder().encodeToString(wav);
            js.append("  ").append(names[i]).append(": \"data:audio/wav;base64,").append(b64).append("\"");
            js.append(i < names.length - 1 ? ",\n" : "\n");
            System.out.println("  -> " + wav.length + " bytes wav");
            System.out.flush();
        }
        js.append("};\n");
        java.io.File outDir = new File(webDir);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        java.io.FileWriter fw = new java.io.FileWriter(new File(outDir, "audio.js"));
        fw.write(js.toString());
        fw.close();
        System.out.println("wrote " + new File(webDir, "audio.js").getPath());
    }

    static byte[] render(File mid, float sampleRate, int maxSeconds) throws Exception {
        Sequence seq = MidiSystem.getSequence(mid);
        AudioSynthesizer synth = (AudioSynthesizer) MidiSystem.getSynthesizer();
        AudioFormat fmt = new AudioFormat(sampleRate, 16, 1, true, false);
        AudioInputStream stream = synth.openStream(fmt, null);

        Sequencer seqr = MidiSystem.getSequencer(false);
        seqr.open();
        seqr.getTransmitter().setReceiver(synth.getReceiver());
        seqr.setSequence(seq);
        seqr.start();

        ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[8192];
        long start = System.currentTimeMillis();
        // 字节上限 = maxSeconds × (sampleRate × 2字节) × 1.2 余量
        long maxBytes = (long) (maxSeconds * sampleRate * 2 * 1.2);

        long capMs = maxSeconds * 1000L;
        boolean truncated = false;
        // 实时配速：序列器按墙钟投递事件，读取若超前会导致节奏失真。
        // 每轮读完后，若已产出字节数超过"墙钟 × 每毫秒字节数 × 1.05"则等待。
        long bytesPerMs = (long) (sampleRate * 2 / 1000);
        if (bytesPerMs < 1) {
            bytesPerMs = 1;
        }
        while (seqr.isRunning()) {
            int n = stream.read(buf, 0, buf.length);
            if (n > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > maxBytes) {
                    truncated = true;
                    break;
                }
            }
            long expected = (System.currentTimeMillis() - start) * bytesPerMs * 105 / 100;
            while (bos.size() > expected) {
                Thread.sleep(10);
                expected = (System.currentTimeMillis() - start) * bytesPerMs * 105 / 100;
            }
            if (System.currentTimeMillis() - start > capMs) {
                truncated = true;
                break;
            }
        }
        if (truncated) {
            System.out.println("  (truncated at " + maxSeconds + "s, bos=" + bos.size() + ")");
        }
        // 先停 sequencer（否则合成器持续产出，收尾循环会以离线速度无限读入）
        seqr.stop();
        // 收尾混响衰减：限 400ms 墙钟 + 512KB 字节双上限
        long tail = System.currentTimeMillis() + 400;
        int tailBytes = 0;
        while (System.currentTimeMillis() < tail && tailBytes < 512 * 1024) {
            int n = stream.read(buf, 0, buf.length);
            if (n > 0) {
                bos.write(buf, 0, n);
                tailBytes += n;
            }
        }
        seqr.close();
        synth.close();

        byte[] pcm = trimTailSilence(bos.toByteArray(), sampleRate);
        pcm = applySeamFades(pcm, sampleRate);

        AudioInputStream out = new AudioInputStream(new ByteArrayInputStream(pcm), fmt, pcm.length / 2);
        File tmp = File.createTempFile("render", ".wav");
        AudioSystem.write(out, AudioFileFormat.Type.WAVE, tmp);
        byte[] wav = java.nio.file.Files.readAllBytes(tmp.toPath());
        
        tmp.delete();
        return wav;
    }

    /** 16bit mono：裁掉尾部静音（保留 80ms 余量），让 BGM 循环无缝。 */
    static byte[] trimTailSilence(byte[] pcm, float sampleRate) {
        int threshold = 300;
        int lastLoud = 0;
        for (int i = pcm.length - 2; i >= 0; i -= 2) {
            int sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            if (Math.abs(sample) > threshold) {
                lastLoud = i + 2;
                break;
            }
        }
        int keep = Math.min(pcm.length, lastLoud + (int) (0.08 * sampleRate) * 2);
        keep = Math.max(keep, 2);
        byte[] out = new byte[keep];
        System.arraycopy(pcm, 0, out, 0, keep);
        return out;
    }

    /** 首尾各 30ms 淡入淡出：消除起播爆音与循环接缝咔哒。 */
    static byte[] applySeamFades(byte[] pcm, float sampleRate) {
        int fadeBytes = (int) (sampleRate * 0.03f) * 2;
        if (fadeBytes * 2 > pcm.length) {
            return pcm;
        }
        for (int i = 0; i < fadeBytes; i += 2) {
            int s = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            s = (int) (s * (i / (float) fadeBytes));
            pcm[i] = (byte) (s & 0xFF);
            pcm[i + 1] = (byte) ((s >> 8) & 0xFF);
        }
        for (int i = 0; i < fadeBytes; i += 2) {
            int p = pcm.length - fadeBytes + i;
            int s = (short) ((pcm[p] & 0xFF) | (pcm[p + 1] << 8));
            s = (int) (s * (1f - i / (float) fadeBytes));
            pcm[p] = (byte) (s & 0xFF);
            pcm[p + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return pcm;
    }
}
