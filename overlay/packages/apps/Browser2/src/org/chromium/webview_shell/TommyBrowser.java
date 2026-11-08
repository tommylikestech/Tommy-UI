package org.chromium.webview_shell;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import android.widget.*;
import android.view.inputmethod.EditorInfo;
import java.util.Locale;

public class TommyBrowser extends Activity {
    WebView web; EditText address;
    static boolean remote(Uri u){return u!=null && u.getHost()!=null && ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state); LinearLayout page=new LinearLayout(this);page.setOrientation(1);setContentView(page);
        LinearLayout bar=new LinearLayout(this); page.addView(bar);
        address=new EditText(this);address.setSingleLine();address.setHint("Search or enter address");address.setSelectAllOnFocus(true);
        address.setInputType(17);address.setImeOptions(EditorInfo.IME_ACTION_GO);
        bar.addView(address,new LinearLayout.LayoutParams(0,-2,1));
        Button go=new Button(this);go.setText("Go");bar.addView(go);go.setOnClickListener(v->navigate());
        address.setOnEditorActionListener((v,id,e)->{navigate();return true;});
        Button files=new Button(this);files.setText("Downloads");bar.addView(files);
        files.setOnClickListener(v->startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)));
        web=new WebView(this);page.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest req){
                Uri u=req.getUrl();if(!remote(u))return true;
                if(req.isForMainFrame() && apk(u)){download(u,"","application/vnd.android.package-archive");return true;} return false;
            }
            @Override public void onPageFinished(WebView w,String url){if(!address.hasFocus())address.setText(url);}
        });
        web.setDownloadListener((url,ua,disposition,type,length)->download(Uri.parse(url),disposition,type));
        if(state!=null && web.restoreState(state)!=null)return;
        Uri launch=getIntent().getData();if(remote(launch))visit(launch);
    }
    static boolean apk(Uri u){String p=u.getPath();return p!=null && p.toLowerCase(Locale.ROOT).endsWith(".apk");}
    void navigate(){String s=address.getText().toString().trim();if(s.isEmpty())return;
        Uri u=Uri.parse(s);if(u.getScheme()==null){u=Uri.parse(s.contains(".")&&!s.contains(" ")?"https://"+s:"https://duckduckgo.com/?q="+Uri.encode(s));}
        if(remote(u))visit(u);else Toast.makeText(this,"Enter an HTTP or HTTPS address.",0).show();}
    void visit(Uri u){web.requestFocus();if(apk(u))download(u,"","application/vnd.android.package-archive");else web.loadUrl(u.toString());}
    void download(Uri u,String disposition,String mime){
        if(!remote(u)){Toast.makeText(this,"This download requires a direct web link.",0).show();return;}
        String name=URLUtil.guessFileName(u.toString(),disposition,mime).replaceAll("[^a-zA-Z0-9._ -]","_");
        if(name.startsWith("."))name="download"+name;
        if(("application/vnd.android.package-archive".equals(mime)||apk(u))&&!name.toLowerCase(Locale.ROOT).endsWith(".apk"))name+=".apk";
        final String file=name;
        new AlertDialog.Builder(this).setTitle("Download "+file+"?").setMessage(u.getHost())
            .setNegativeButton("Cancel",null).setPositiveButton("Download",(d,i)->{try{
                DownloadManager.Request req=new DownloadManager.Request(u).setTitle(file)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,file)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                if(file.toLowerCase(Locale.ROOT).endsWith(".apk"))req.setMimeType("application/vnd.android.package-archive");
                getSystemService(DownloadManager.class).enqueue(req);
                Toast.makeText(this,"Download started",0).show();
            }catch(RuntimeException e){Toast.makeText(this,"Download failed: "+e.getMessage(),1).show();}}).show();
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);web.saveState(b);}
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){web.destroy();super.onDestroy();}
}
