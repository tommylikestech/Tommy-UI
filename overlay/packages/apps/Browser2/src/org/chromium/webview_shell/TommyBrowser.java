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
    LinearLayout page, home;
    ProgressBar progress;
    static boolean remote(Uri u){return u!=null && u.getHost()!=null && ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state); page=new LinearLayout(this);page.setOrientation(1);setContentView(page);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xff16392e);
        bar.setPadding(dp(10), dp(6), dp(6), dp(6));
        page.addView(bar);
        address=new EditText(this);address.setSingleLine();address.setHint("Search or enter address");address.setSelectAllOnFocus(true);
        address.setInputType(17);address.setImeOptions(EditorInfo.IME_ACTION_GO);
        android.graphics.drawable.GradientDrawable field = new android.graphics.drawable.GradientDrawable();
        field.setColor(0xffedf5ed);
        field.setCornerRadius(dp(24));
        address.setBackground(field);
        address.setTextSize(16);
        address.setTextColor(0xff182d25);
        address.setHintTextColor(0xff4a6255);
        address.setPadding(dp(16), 0, dp(8), 0);
        bar.addView(address,new LinearLayout.LayoutParams(0,dp(48),1));
        Button go=toolbarButton("Go");bar.addView(go);go.setOnClickListener(v->navigate());
        address.setOnEditorActionListener((v,id,e)->{navigate();return true;});
        Button menu = toolbarButton("⋮");
        menu.setTextSize(22);
        menu.setContentDescription("Browser menu");
        bar.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
        menu.setOnClickListener(v -> {
            PopupMenu options = new PopupMenu(this,menu);
            options.getMenu().add("Home").setEnabled(web!=null).setOnMenuItemClickListener(item -> {
                showHome(); return true;
            });
            options.getMenu().add("Reload").setEnabled(web!=null).setOnMenuItemClickListener(item -> {
                web.reload(); return true;
            });
            options.getMenu().add("Downloads").setOnMenuItemClickListener(item -> {
                startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); return true;
            });
            options.show();
        });
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xff1c473b, 0xff0d241e}));
        home.setPadding(dp(24),dp(32),dp(24),dp(24));
        page.addView(home, new LinearLayout.LayoutParams(-1,0,1));
        TextView title = new TextView(this);
        title.setText("Tommy Browser");
        title.setTextSize(34);
        title.setTextColor(0xffeef6ec);
        home.addView(title);
        Button search = toolbarButton("Search the web");
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(0xffc3e9d2);
        pill.setCornerRadius(dp(24));
        search.setBackground(pill);
        search.setTextColor(0xff173b2e);
        search.setPadding(dp(22), 0, dp(22), 0);
        LinearLayout.LayoutParams searchSize = new LinearLayout.LayoutParams(-2,dp(48));
        searchSize.topMargin = dp(20);
        home.addView(search,searchSize);
        search.setOnClickListener(v -> {
            address.requestFocus();
            getSystemService(android.view.inputmethod.InputMethodManager.class).showSoftInput(
                    address, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(android.view.View.GONE);
        page.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        if(state!=null && state.containsKey("history")) {
            openWeb();
            if(web.restoreState(state.getBundle("history"))!=null) {
                address.setText(web.getUrl());
                return;
            }
            showHome();
        }
        Uri launch=getIntent().getData();if(remote(launch))visit(launch);
    }
    private void openWeb() {
        home.setVisibility(android.view.View.GONE);
        if(web!=null) { web.setVisibility(android.view.View.VISIBLE); return; }
        web=new WebView(this);page.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest req){
                Uri u=req.getUrl();if(!remote(u))return true;
                if(req.isForMainFrame() && apk(u)){download(u,"","application/vnd.android.package-archive");return true;} return false;
            }
            @Override public void onPageFinished(WebView w,String url){if(!address.hasFocus())address.setText(w.getUrl());}
        });
        web.setDownloadListener((url,ua,disposition,type,length)->download(Uri.parse(url),disposition,type));
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view,int value) {
                progress.setProgress(value);
                progress.setVisibility(value<100 && home.getVisibility()!=android.view.View.VISIBLE
                        ? android.view.View.VISIBLE : android.view.View.GONE);
            }
        });
    }
    private void showHome() {
        if(web!=null) {page.removeView(web);web.destroy();web=null;}
        progress.setVisibility(android.view.View.GONE);
        home.setVisibility(android.view.View.VISIBLE);
        address.setText("");
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private Button toolbarButton(String label) {
        Button button = new Button(this, null, android.R.attr.borderlessButtonStyle);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(0xffd9f0de);
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setMinHeight(dp(48));
        return button;
    }
    static boolean apk(Uri u){String p=u.getPath();return p!=null && p.toLowerCase(Locale.ROOT).endsWith(".apk");}
    void navigate(){String s=address.getText().toString().trim();if(s.isEmpty())return;
        Uri u=Uri.parse(s);if(u.getScheme()==null){u=Uri.parse(s.contains(".")&&!s.contains(" ")?"https://"+s:"https://duckduckgo.com/?q="+Uri.encode(s));}
        if(remote(u))visit(u);else Toast.makeText(this,"Enter an HTTP or HTTPS address.",0).show();}
    void visit(Uri u){if(apk(u)){download(u,"","application/vnd.android.package-archive");return;}
        openWeb();web.requestFocus();
        getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(address.getWindowToken(),0);
        web.loadUrl(u.toString());}
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
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(web!=null){Bundle history=new Bundle(); if(web.saveState(history)!=null)b.putBundle("history",history); }}
    @Override public void onBackPressed(){if(web==null)super.onBackPressed();else if(web.canGoBack())web.goBack();else showHome();}
    @Override protected void onDestroy(){if(web!=null){page.removeView(web);web.destroy();}super.onDestroy();}
}
