package dev.zcode.android;
import android.app.Activity;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.*;
import java.io.*;
import java.util.Arrays;
import java.util.Map;

public class TerminalScreen extends Activity {
    static final class LocalPage extends WebViewClient {
        public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
    }
    WebView view;
    Process child;
    public void onCreate(Bundle saved){
        super.onCreate(saved);
        view=new WebView(this);
        view.setWebViewClient(new LocalPage());
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setBlockNetworkLoads(true);
        view.getSettings().setAllowContentAccess(false);
        view.addJavascriptInterface(this,"Shell");
        setContentView(view);
        view.loadUrl("file:///android_asset/terminal.html");
    }
    @JavascriptInterface public synchronized void start(int rows,int cols){
        if(child!=null||isFinishing()||isDestroyed())return;
        File rt=new File(getFilesDir(),Studio.RUNTIME);
        String bash=new File(rt,"bin/bash").toString();
        ProcessBuilder pb=new ProcessBuilder(new File(rt,"bin/pty").toString(),bash,String.valueOf(rows),String.valueOf(cols));
        LocalRuntime.environment(this,pb,rt);
        pb.environment().put("TERM","xterm-256color");
        pb.environment().put("PS1","zcode:\\w $ ");
        try{
            Process process=pb.start();child=process;
            new Thread(()->{
                try(InputStream in=process.getInputStream()){
                    byte[] buf=new byte[4096];int n;
                    while((n=in.read(buf))!=-1)display(Arrays.copyOf(buf,n));
                }catch(IOException ignored){}
                runOnUiThread(()->{if(!isFinishing())finish();});
            }).start();
        }catch(IOException e){display(e.toString().getBytes());}
    }
    void display(byte[] bytes){
        String data=Base64.encodeToString(bytes,Base64.NO_WRAP);
        runOnUiThread(()->{if(!isDestroyed())view.evaluateJavascript("receive('"+data+"')",null);});
    }
    @JavascriptInterface public synchronized void send(String text){
        if(child==null)return;
        try{child.getOutputStream().write(text.getBytes("UTF-8"));child.getOutputStream().flush();}catch(IOException ignored){}
    }
    protected synchronized void onDestroy(){
        if(child!=null)child.destroyForcibly();
        view.removeJavascriptInterface("Shell");
        view.destroy();super.onDestroy();
    }
}
