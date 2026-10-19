import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/** 从 jar 内 32x32 图标最近邻放大生成 PWA 图标 180/192/512。 */
public class MakeIcons {
    public static void main(String[] args) throws Exception {
        String jarPath = args.length > 0 ? args[0] : "../皇家骑士团.jar";
        String outDir = args.length > 1 ? "web/icons" : args[1];

        java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath);
        java.util.jar.JarEntry entry = jar.getJarEntry("32_32.png");
        InputStream in = jar.getInputStream(entry);
        BufferedImage src = ImageIO.read(in);
        in.close();
        jar.close();
        System.out.println("src icon: " + src.getWidth() + "x" + src.getHeight());

        int[] sizes = {180, 192, 512};
        for (int s : sizes) {
            BufferedImage out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            double ratio = src.getWidth() / (double) src.getHeight();
            // 居中适配 + 透明底（源图为正方形则直读）
            int w, h, ox, oy;
            if (ratio >= 1.0) {
                w = s; h = (int) Math.round(s / ratio); ox = 0; oy = (s - h) / 2;
            } else {
                h = s; w = (int) Math.round(s * ratio); ox = (s - w) / 2; oy = 0;
            }
            for (int y = 0; y < h; y++) {
                int sy = Math.min(src.getHeight() - 1, y * src.getHeight() / h);
                for (int x = 0; x < w; x++) {
                    int sx = Math.min(src.getWidth() - 1, x * src.getWidth() / w);
                    out.setRGB(ox + x, oy + y, src.getRGB(sx, sy));
                }
            }
            File f = new File(outDir, "icon-" + s + ".png");
            ImageIO.write(out, "png", f);
            System.out.println("wrote " + f.getPath());
        }
    }
}
