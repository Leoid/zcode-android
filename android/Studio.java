package dev.zcode.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class Studio extends Activity {
    static final String RUNTIME="studio-v3";
    private WebView web;
    private TextView status;
    private volatile Process host;
    private String origin;
    private volatile boolean closing;
    private volatile boolean ready;
    private ValueCallback<Uri[]> chooser;
    private final String token=UUID.randomUUID().toString()+UUID.randomUUID();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff12171e);
        getWindow().setNavigationBarColor(0xff12171e);
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(1);screen.setBackgroundColor(0xff17191d);
        LinearLayout bar=new LinearLayout(this);screen.addView(bar);
        nav(bar,"Chats",()->web.evaluateJavascript("document.querySelector('[data-testid=settings-back-button]')?.click();document.documentElement.classList.toggle('android-history')",null));
        nav(bar,"Files",()->startActivity(new Intent(this,FilesScreen.class)));
        nav(bar,"Terminal",()->startActivity(new Intent(this,TerminalScreen.class)));
        nav(bar,"Settings",()->web.evaluateJavascript("document.querySelector('[data-testid=task-settings-button]')?.click()",null));
        FrameLayout frame=new FrameLayout(this);screen.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        frame.setBackgroundColor(0xff12171e);
        web=new WebView(this);
        web.setBackgroundColor(0xff12171e);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new Navigation());
        web.setWebChromeClient(new Documents());
        frame.addView(web);
        status=new TextView(this);
        status.setBackgroundColor(0xff12171e);
        status.setTextColor(0xffe0ebff);
        status.setGravity(Gravity.CENTER);
        status.setText("ZCode\n\nOpening your workspace…");
        frame.addView(status);setContentView(screen);
        new Thread(this::startHost,"zcode-studio").start();
    }
    void nav(LinearLayout bar,String name,Runnable run){
        Button b=new Button(this,null,android.R.attr.borderlessButtonStyle);b.setTextSize(13);b.setText(name);b.setAllCaps(false);b.setContentDescription(name);b.setOnClickListener(v->{if(ready)run.run();});bar.addView(b,new LinearLayout.LayoutParams(0,-2,1));
    }
    final class Navigation extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
            Uri u=request.getUrl();
            if(origin!=null && u.toString().startsWith(origin+"/"))return false;
            if(request.hasGesture() && ("https".equals(u.getScheme())||"http".equals(u.getScheme())))
                try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
            return true;
        }
    }
    final class Documents extends WebChromeClient {
        @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> result, FileChooserParams params){
            if(chooser!=null)chooser.onReceiveValue(null);
            chooser=result;
            try{startActivityForResult(params.createIntent(),21);return true;}
            catch(Exception e){chooser=null;return false;}
        }
    }
    protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==21 && chooser!=null){chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));chooser=null;}
    }
    void unpack(String asset,File root)throws IOException{
        try(ZipInputStream in=new ZipInputStream(getAssets().open(asset))){
            ZipEntry entry;
            while((entry=in.getNextEntry())!=null){
                File path=new File(root,entry.getName());
                if(!path.getCanonicalPath().startsWith(root.getCanonicalPath()+"/"))throw new IOException("Invalid asset path");
                if(entry.isDirectory()){path.mkdirs();continue;}
                path.getParentFile().mkdirs();
                Files.copy(in,path.toPath(),StandardCopyOption.REPLACE_EXISTING);
                if(entry.getName().startsWith("bin/")||entry.getName().startsWith("libexec/"))path.setExecutable(true,true);
            }
        }
    }
    private void startHost(){
        try{
            File home=getFilesDir(), rt=new File(home,RUNTIME);
            File marker=new File(rt,"ready-"+getPackageManager().getPackageInfo(getPackageName(),0).lastUpdateTime);
            if(!marker.exists()){
                rt.mkdirs();unpack("runtime.zip",rt);unpack("studio.zip",rt);
                for(String name:new String[]{"zcode.cjs","builtin.json","zcode","pty"}){
                    File dest=new File(rt,name.equals("zcode")||name.equals("pty")?"bin/"+name:name);
                    try(InputStream i=getAssets().open(name)){Files.copy(i,dest.toPath(),StandardCopyOption.REPLACE_EXISTING);}
                    if(dest.getParentFile().getName().equals("bin"))dest.setExecutable(true,true);
                }
                marker.createNewFile();
            }
            File workspace=new File(home,"workspace");workspace.mkdirs();
            int port=getPreferences(0).getInt("port",0);
            if(port==0){try(ServerSocket probe=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=probe.getLocalPort();}
                getPreferences(0).edit().putInt("port",port).apply();}
            origin="http://127.0.0.1:"+port;
            File node=new File(rt,"bin/node");
            ProcessBuilder pb=new ProcessBuilder(node.toString(),new File(rt,"host.mjs").toString());
            LocalRuntime.environment(this,pb,rt);
            Map<String,String> env=pb.environment();
            env.put("LANG",Locale.getDefault().toLanguageTag().replace('-','_')+".UTF-8");
            env.remove("ZCODE_PERSONAL_PROVIDER_CONFIG_FILE");
            env.put("PORT",String.valueOf(port));env.put("ZCODE_SERVER_HOST","127.0.0.1");
            env.put("ZCODE_SERVER_AUTH_TOKEN",token);env.put("ZCODE_WEB_STATIC_ROOT",new File(rt,"web").toString());
            env.put("ZCODE_SERVER_WORKSPACE",workspace.toString());
            env.put("ZCODE_AGENT_SERVER_COMMAND",node.toString());
            env.put("ZCODE_AGENT_SERVER_ARGS_JSON",new org.json.JSONArray().put(new File(rt,"zcode.cjs").toString()).put("app-server").put("--stdio").toString());
            File diagnostic=new File(getCacheDir(),"studio.log");
            pb.redirectOutput(diagnostic);
            host=pb.start();
            while(!closing && host.isAlive()){
                HttpURLConnection c=(HttpURLConnection)new URL(origin+"/api/server-info").openConnection();
                c.setRequestProperty("Authorization","Bearer "+token);
                c.setConnectTimeout(500);c.setReadTimeout(1000);
                try{if(c.getResponseCode()==200)break;}catch(IOException ignored){}finally{c.disconnect();}
                Thread.sleep(150);
            }
            if(closing){host.destroy();return;}
            if(!host.isAlive())throw new IOException("Workspace service exited. "+new String(Files.readAllBytes(diagnostic.toPath())));
            runOnUiThread(()->android.webkit.CookieManager.getInstance().setCookie(origin,"zcode_lite_token="+token+"; HttpOnly; SameSite=Strict; Path=/",ok->{
                if(closing)return;
                if(!ok){status.setText("Workspace authentication failed");return;}
                ready=true;web.loadUrl(origin+"/");status.setVisibility(android.view.View.GONE);
            }));
        }catch(Exception e){runOnUiThread(()->status.setText("Could not open ZCode\n\n"+e.getMessage()));}
    }
    @Override public void onBackPressed(){web.evaluateJavascript("(()=>{const back=document.querySelector('[data-testid=settings-back-button]');if(back){back.click();return true;}return document.documentElement.classList.contains('android-history')&&(document.documentElement.classList.remove('android-history'),true);})()",handled->{if(!"true".equals(handled)){if(web.canGoBack())web.goBack();else moveTaskToBack(true);}});}
    @Override protected void onDestroy(){closing=true;if(host!=null)host.destroy();web.destroy();super.onDestroy();}
}
