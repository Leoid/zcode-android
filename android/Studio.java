package dev.zcode.android;

import android.app.Activity;
import android.content.*;
import android.os.IBinder;
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
    private WorkspaceService.Session session;
    private boolean bound;
    private final WorkspaceService.Listener listener=this::workspaceChanged;
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){session=(WorkspaceService.Session)binder;session.attach(listener);}
        public void onServiceDisconnected(ComponentName name){session=null;ready=false;status.setText("Workspace disconnected");status.setVisibility(android.view.View.VISIBLE);}
    };
    private void workspaceChanged(String address,String credential,String error){
        if(closing)return;
        if(error!=null){
            ready=false;status.setText(error);status.setVisibility(android.view.View.VISIBLE);
            if("Workspace closed".equals(error))finishAndRemoveTask();
            return;
        }
        origin=address;
        android.webkit.CookieManager.getInstance().setCookie(origin,"zcode_lite_token="+credential+"; HttpOnly; SameSite=Strict; Path=/",ok->{
            if(closing)return;
            if(!ok){status.setText("Workspace authentication failed");return;}
            ready=true;web.loadUrl(origin+"/");status.setVisibility(android.view.View.GONE);
        });
    }
    private String origin;
    private volatile boolean closing;
    private volatile boolean ready;
    private ValueCallback<Uri[]> chooser;


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
        Intent host=new Intent(this,WorkspaceService.class);
        startForegroundService(host);
        bound=bindService(host,connection,BIND_AUTO_CREATE);
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
    @Override public void onBackPressed(){web.evaluateJavascript("(()=>{const back=document.querySelector('[data-testid=settings-back-button]');if(back){back.click();return true;}return document.documentElement.classList.contains('android-history')&&(document.documentElement.classList.remove('android-history'),true);})()",handled->{if(!"true".equals(handled)){if(web.canGoBack())web.goBack();else moveTaskToBack(true);}});}
    @Override protected void onDestroy(){closing=true;if(session!=null)session.detach(listener);if(bound)unbindService(connection);web.destroy();super.onDestroy();}
}
