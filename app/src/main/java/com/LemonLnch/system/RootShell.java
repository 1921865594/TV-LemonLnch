package com.LemonLnch.system;

import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Root/ADB-like bridge. The APK only uses it when su is available. */
public final class RootShell {
    private RootShell() {}

    public static final class Result {
        public final int code;
        public final String output;
        public final String error;
        Result(int code, String output, String error) {
            this.code = code;
            this.output = output == null ? "" : output;
            this.error = error == null ? "" : error;
        }
        public boolean ok() { return code == 0; }
    }

    public static boolean isRootAvailable() {
        Result r = exec("id");
        return r.ok() && r.output.contains("uid=0");
    }

    public static Result exec(String command) {
        if (TextUtils.isEmpty(command)) return new Result(-1, "", "empty command");
        Process p = null;
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        try {
            p = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(false)
                    .start();
            final Process process = p;
            Thread outReader = new Thread(() -> read(process.getInputStream(), out), "root-out");
            Thread errReader = new Thread(() -> read(process.getErrorStream(), err), "root-err");
            outReader.start();
            errReader.start();
            if (!p.waitFor(3500, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                return new Result(-2, out.toString(), "timeout\n" + err);
            }
            outReader.join(200);
            errReader.join(200);
            return new Result(p.exitValue(), out.toString(), err.toString());
        } catch (Exception e) {
            return new Result(-3, out.toString(), e.toString());
        } finally {
            if (p != null) p.destroy();
        }
    }

    private static void read(java.io.InputStream in, StringBuilder target) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) target.append(line).append('\n');
        } catch (Exception ignored) {
        }
    }

    public static void configureNonSdkAccess() {
        if (Build.VERSION.SDK_INT == 28) {
            exec("settings put global hidden_api_policy_pre_p_apps 1; " +
                    "settings put global hidden_api_policy_p_apps 1");
        } else if (Build.VERSION.SDK_INT >= 29) {
            exec("settings put global hidden_api_policy 1");
        }
    }
}
