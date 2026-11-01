package dev.zcode.android;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

public final class FilesScreen extends Activity {
    File root,dir,selected;
    LinearLayout page,rows;
    EditText editor;
    String original;
    int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    public void onCreate(Bundle state){super.onCreate(state);root=new File(getFilesDir(),"workspace");root.mkdirs();dir=root;list();}
    void init(String title){
        page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(12),dp(8),dp(12),0);setContentView(page);
        TextView heading=new TextView(this);heading.setText(title);heading.setTextSize(22);heading.setPadding(dp(4),dp(8),0,dp(6));page.addView(heading);
    }
    void action(LinearLayout bar,String label,Runnable run){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setOnClickListener(v->run.run());bar.addView(b);}
    void list(){
        selected=null;editor=null;init(dir.equals(root)?"Workspace files":root.toPath().relativize(dir.toPath()).toString());
        LinearLayout bar=new LinearLayout(this);page.addView(bar);action(bar,"Back",this::onBackPressed);action(bar,"New",this::create);action(bar,"Import",()->new AlertDialog.Builder(this).setItems(new String[]{"File from device","Project folder from device"},(dialog,index)->{
            if(index==0)startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),1);
            else startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),3);
        }).show());
        ScrollView scroll=new ScrollView(this);page.addView(scroll);rows=new LinearLayout(this);rows.setOrientation(1);scroll.addView(rows);
        File[] files=dir.listFiles();if(files==null)return;
        Arrays.sort(files,Comparator.comparing(File::isFile).thenComparing(File::getName,String.CASE_INSENSITIVE_ORDER));
        if(files.length==0){TextView empty=new TextView(this);empty.setText("This folder is empty. Create a file or import one from your phone.");empty.setPadding(dp(8),dp(20),dp(8),0);rows.addView(empty);}
        for(File f:files){TextView row=new TextView(this);row.setText((f.isDirectory()?"▸   ":"     ")+f.getName());row.setTextSize(16);row.setPadding(dp(6),dp(13),0,dp(13));rows.addView(row);row.setOnClickListener(v->{try{if(!f.getCanonicalFile().toPath().startsWith(root.getCanonicalFile().toPath()))throw new IOException("Link leaves workspace");if(f.isDirectory()){dir=f;list();}else open(f);}catch(Exception e){error(e);}});}
    }
    void create(){
        EditText name=new EditText(this);name.setSingleLine();name.setHint("Name");
        new AlertDialog.Builder(this).setTitle("Create in this folder").setView(name).setNegativeButton("Cancel",null)
            .setNeutralButton("Folder",(d,w)->create(name.getText().toString(),true))
            .setPositiveButton("File",(d,w)->create(name.getText().toString(),false)).show();
    }
    File child(String name)throws IOException{
        if(name.trim().isEmpty()||name.equals(".")||name.equals("..")||name.contains("/"))throw new IOException("Choose a filename");
        File f=new File(dir,name);if(f.exists())throw new IOException("That name already exists");return f;
    }
    void create(String name,boolean folder){try{File f=child(name);if(folder){if(!f.mkdir())throw new IOException("Could not create folder");list();}else{Files.createFile(f.toPath());open(f);}}catch(Exception e){error(e);}}
    void open(File f)throws IOException{
        selected=f;init(f.getName());LinearLayout bar=new LinearLayout(this);page.addView(bar);action(bar,"Back",this::onBackPressed);
        action(bar,"Export",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,f.getName()),2));
        if(f.length()<=2*1024*1024){
            try{String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(f.toPath()))).toString();
                if(text.indexOf(0)<0){original=text;editor=new EditText(this);editor.setGravity(Gravity.TOP);editor.setTypeface(Typeface.MONOSPACE);editor.setTextSize(14);editor.setText(text);page.addView(editor,new LinearLayout.LayoutParams(-1,-1));
                    action(bar,"Save",()->{try{String current=new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);if(!current.equals(original))throw new IOException("File changed on disk. Reopen it before saving.");String next=editor.getText().toString();File temp=File.createTempFile(".edit-",".tmp",dir);try{Files.write(temp.toPath(),next.getBytes(StandardCharsets.UTF_8));Files.move(temp.toPath(),f.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{temp.delete();}original=next;Toast.makeText(this,"Saved",0).show();}catch(Exception e){error(e);}});return;}
            }catch(CharacterCodingException ignored){}
        }
        TextView info=new TextView(this);info.setText(f.length()+" bytes · Export to open in another app");page.addView(info);
    }
    protected void onActivityResult(int code,int status,Intent data){
        super.onActivityResult(code,status,data);if(status!=RESULT_OK||data==null||data.getData()==null)return;
        if(code==3){importFolder(data.getData());return;}
        try{if(code==1){String name="imported-file";try(android.database.Cursor c=getContentResolver().query(data.getData(),new String[]{"_display_name"},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
                File dest=child(name);try(InputStream in=getContentResolver().openInputStream(data.getData())){Files.copy(in,dest.toPath());}list();}
            if(code==2 && selected!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){Files.copy(selected.toPath(),out);}}
        }catch(Exception e){error(e);}
    }
    void importFolder(android.net.Uri tree){
        File parent=dir;
        ProgressDialog progress=ProgressDialog.show(this,"Importing project","Copying files into the workspace…",true,false);
        new Thread(()->{
            File temp=null;
            try{
                String id=android.provider.DocumentsContract.getTreeDocumentId(tree);
                android.net.Uri doc=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,id);
                String name;
                try(android.database.Cursor c=getContentResolver().query(doc,new String[]{"_display_name"},null,null,null)){
                    if(c==null||!c.moveToFirst())throw new IOException("Cannot read directory");name=c.getString(0);
                }
                File dest=safeChild(parent,name);
                temp=Files.createTempDirectory(parent.toPath(),".incoming-").toFile();
                copyChildren(tree,id,temp,new HashSet<String>());
                Files.move(temp.toPath(),dest.toPath());temp=null;
                runOnUiThread(()->{dir=dest;list();});
            }catch(Exception e){runOnUiThread(()->error(e));}
            finally{if(temp!=null)discardCopy(temp);runOnUiThread(progress::dismiss);}
        },"zcode-import").start();
    }
    File safeChild(File parent,String name)throws IOException{
        if(name==null||name.isEmpty()||name.contains("/")||name.equals(".")||name.equals(".."))throw new IOException("Invalid entry name");
        File dest=new File(parent,name);
        if(dest.exists())throw new IOException(name+" already exists");
        return dest;
    }
    void copyChildren(android.net.Uri tree,String id,File parent,Set<String> seen)throws IOException{
        if(!seen.add(id))throw new IOException("Directory cycle");
        android.net.Uri children=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        try(android.database.Cursor c=getContentResolver().query(children,new String[]{"document_id","_display_name","mime_type"},null,null,null)){
            if(c==null)throw new IOException("Cannot list directory");
            while(c.moveToNext()){
                String item=c.getString(0);File dest=safeChild(parent,c.getString(1));
                if(android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))){
                    Files.createDirectory(dest.toPath());copyChildren(tree,item,dest,seen);
                }else try(InputStream in=getContentResolver().openInputStream(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,item))){
                    Files.copy(in,dest.toPath());
                }
            }
        }
    }
    void discardCopy(File f){File[] children=f.listFiles();if(children!=null)for(File child:children)discardCopy(child);f.delete();}
    void error(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    @Override public void onBackPressed(){if(selected!=null){if(editor!=null&&!editor.getText().toString().equals(original)){new AlertDialog.Builder(this).setMessage("Discard unsaved changes?").setNegativeButton("Keep editing",null).setPositiveButton("Discard",(d,w)->list()).show();}else list();}else if(!dir.equals(root)){dir=dir.getParentFile();list();}else super.onBackPressed();}
}
