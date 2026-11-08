package com.tommyui.storage;
import android.app.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.*;

public class AboutTommy extends Activity {
    int taps; long last;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(1); int pad=(int)(24*getResources().getDisplayMetrics().density);
        page.setPadding(pad,pad,pad,pad); setContentView(page);
        TextView heading=new TextView(this); heading.setText("Tommy UI"); heading.setTextSize(36); page.addView(heading);
        Button version=new Button(this); version.setText("Tommy UI version 1.003"); page.addView(version);
        version.setOnClickListener(v->{long now=SystemClock.elapsedRealtime(); if(now-last>1300)taps=0; last=now;
            if(++taps==7){taps=0; constellation();}});
        TextView detail=new TextView(this); detail.setText("Android "+android.os.Build.VERSION.RELEASE+"  ·  "+android.os.Build.VERSION.INCREMENTAL); page.addView(detail);
        Button update=new Button(this); update.setText("System update"); page.addView(update);
        update.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Connect to USB to update")
            .setMessage("Connect your phone to your PC with a USB cable. Enable USB debugging, then open the Tommy USB Updater included in the new flash kit.")
            .setPositiveButton("OK",null).show());
    }
    private void constellation(){
        TextView sky=new TextView(this); sky.setGravity(Gravity.CENTER); sky.setTextSize(25);
        sky.setPadding(16,64,16,64); sky.setBackgroundColor(0xff102f32); sky.setTextColor(0xffcdf9da);
        String[] stars={"✦","✦    ✧","✦    ✧\n       ✦","✦    ✧\n       ✦     ✧","✦    ✧\n       ✦     ✧\n         ✦","Tommy's constellation  ✨"};
        int[] count={0};sky.setText(stars[0]);sky.setOnClickListener(v->sky.setText(stars[Math.min(++count[0],stars.length-1)]));
        new AlertDialog.Builder(this).setTitle("Touch the stars").setView(sky).setPositiveButton("Done",null).show();
    }
}
