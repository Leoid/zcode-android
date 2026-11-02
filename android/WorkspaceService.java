package dev.zcode.android;

import android.app.*;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Owns the local agent while the user switches apps or the activity is recreated. */
public class WorkspaceService extends Service {
    private static final String CHANNEL="local-workspace";
    private static final int NOTIFICATION=17;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final String token=UUID.randomUUID().toString()+UUID.randomUUID();
    private volatile Process host;
    private volatile boolean closing;
    private String origin, error;
    private boolean ready;
    private Listener listener;
    interface Listener {void changed(String origin,String token,String error);}
    final class Session extends Binder {
        void attach(Listener next){listener=next;notifyClient();}
        void detach(Listener old){if(listener==old)listener=null;}
    }
    private final Session session=new Session();
    @Override public IBinder onBind(Intent intent){return session;}
    private void notifyClient(){if(listener!=null&&(ready||error!=null))listener.changed(origin,token,error);}
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Local workspace",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,Studio.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent close=PendingIntent.getService(this,1,new Intent(this,WorkspaceService.class).setAction("close"),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("ZCode workspace is open").setContentText("The agent can keep working while you use other apps.")
            .setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Close workspace",close).build();
        startForeground(NOTIFICATION,n);
        new Thread(this::startHost,"zcode-workspace").start();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && "close".equals(intent.getAction())){
            if(listener!=null)listener.changed(null,null,"Workspace closed");
            shutdown();stopSelf();
        }
        return START_NOT_STICKY;
    }
    private synchronized void shutdown(){
        closing=true;Process p=host;if(p!=null){try{p.getOutputStream().close();}catch(IOException ignored){}p.destroy();}
        stopForeground(STOP_FOREGROUND_REMOVE);
    }
    @Override public void onDestroy(){shutdown();listener=null;super.onDestroy();}
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
            File home=getFilesDir(), rt=new File(home,Studio.RUNTIME);
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
            int port=getSharedPreferences("Studio",MODE_PRIVATE).getInt("port",0);
            if(port==0){try(ServerSocket probe=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=probe.getLocalPort();}
                getSharedPreferences("Studio",MODE_PRIVATE).edit().putInt("port",port).apply();}
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
            synchronized(this){
                if(closing)return;
                host=pb.start();
            }
            while(!closing && host.isAlive()){
                HttpURLConnection c=(HttpURLConnection)new URL(origin+"/api/server-info").openConnection();
                c.setRequestProperty("Authorization","Bearer "+token);
                c.setConnectTimeout(500);c.setReadTimeout(1000);
                try{if(c.getResponseCode()==200)break;}catch(IOException ignored){}finally{c.disconnect();}
                Thread.sleep(150);
            }
            if(closing)return;
            if(!host.isAlive())throw new IOException("Workspace service exited. "+new String(Files.readAllBytes(diagnostic.toPath())));
            main.post(()->{if(!closing){ready=true;notifyClient();}});
            int exit=host.waitFor();
            if(!closing)throw new IOException("Workspace service exited ("+exit+")");
        }catch(Exception e){
            main.post(()->{if(!closing){ready=false;error=e.getMessage();notifyClient();shutdown();stopSelf();}});
        }
    }
}
