import java.io.File;
import java.util.List;
import org.teavm.tooling.TeaVMTool;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.vm.TeaVMOptimizationLevel;

/**
 * TeaVM 编译驱动：把 游戏jar + MIDP桩层 编译成单个 game.js。
 * 用 JDK21 单文件源码模式运行：
 *   java -cp "tools/lib/*;stubs/classes;皇家骑士团.jar" tools/src/Build.java [mainClass] [outDir]
 */
public class Build {
    public static void main(String[] args) throws Exception {
        String mainClass = args.length > 0 ? args[0] : "host.HostMain";
        String outDir = args.length > 1 ? args[1] : "dist";
        String level = args.length > 2 ? args[2] : "ADVANCED";

        // classpath：游戏 jar + 桩层 class + 桩层资源目录(services 注册) + lib 本身
        String here = System.getProperty("user.dir");
        System.out.println("user.dir = " + here);

        TeaVMTool t = new TeaVMTool();
        t.setTargetType(TeaVMTargetType.JAVASCRIPT);
        t.setMainClass(mainClass);
        t.setTargetDirectory(new File(outDir));
        t.setTargetFileName("game.js");
        t.setOptimizationLevel(TeaVMOptimizationLevel.valueOf(level));
        t.setSourceMapsFileGenerated(true);
        t.setDebugInformationGenerated(true);

        ClassLoader cl = Build.class.getClassLoader(); // 由 -cp 提供：游戏jar+stubs+lib
        t.setClassLoader(cl);
        // classPath 供工具定位类文件（与 -cp 内容一致）
        String cp = System.getProperty("java.class.path");
        java.util.ArrayList<File> entries = new java.util.ArrayList<>();
        for (String e : cp.split(java.io.File.pathSeparator)) {
            if (!e.isEmpty()) entries.add(new File(e));
        }
        t.setClassPath(entries);
        t.setLog(new org.teavm.tooling.ConsoleTeaVMToolLog(true));
        t.generate();
        // dump 全部编译问题（工具本身只打计数）
        org.teavm.diagnostics.ProblemProvider pp = t.getProblemProvider();
        if (pp != null) {
            for (org.teavm.diagnostics.Problem p : pp.getProblems()) {
                System.out.println("[TEAVM-" + p.getSeverity() + "] " + p.getLocation() + " : " + p.getText());
            }
        }
        System.out.println("generate() 完成 -> " + outDir + "/game.js");
    }
}
