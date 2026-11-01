package dev.zcode.android;

import android.app.Activity;
import java.util.Map;
import java.io.File;

final class LocalRuntime {
    static void environment(Activity activity,ProcessBuilder pb,File runtime){
        Map<String,String> env=pb.environment();
        File home=activity.getFilesDir();
        pb.directory(new File(home,"workspace")).redirectErrorStream(true);
        env.put("PATH",new File(runtime,"bin")+":/system/bin");
        env.put("LD_LIBRARY_PATH",new File(runtime,"lib").toString());
        env.put("HOME",home.toString());
        env.put("TMPDIR",activity.getCacheDir().toString());
        env.put("GIT_EXEC_PATH",new File(runtime,"libexec/git-core").toString());
        env.put("GIT_TEMPLATE_DIR",new File(runtime,"share/git-core/templates").toString());
        env.put("GIT_SSL_CAINFO",new File(runtime,"etc/tls/cert.pem").toString());
        env.put("GIT_PAGER","cat");
        env.put("SHELL",new File(runtime,"bin/bash").toString());
        env.put("ZCODE_ANDROID_RUNTIME",runtime.toString());
        env.put("ZCODE_BUILTIN_PROVIDER_CONFIG_FILE",new File(runtime,"builtin.json").toString());
        env.put("ZCODE_PERSONAL_PROVIDER_CONFIG_FILE",new File(home,".zcode/v2/provider_config.json").toString());
    }
}
