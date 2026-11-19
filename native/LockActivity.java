package com.aeterna.vitrix;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class LockActivity extends Activity {
    static LockActivity cur;
    private ImageView bg;
    private TextView tTitle, tArtist, tCur, tDur, bPlay, bFav;
    private SeekBar sb;
    private boolean dragging = false;
    private final Handler h = new Handler(Looper.getMainLooper());
    private GestureDetector gd;

    private final Runnable tick = new Runnable() {
        public void run() { updateTime(); h.postDelayed(this, 1000); }
    };

    static void closeIt() { if (cur != null) cur.finish(); }
    static void refresh() { if (cur != null) cur.runOnUiThread(new Runnable() { public void run() { if (cur != null) cur.fill(); } }); }

    private String fmt(double s) { int t = (int) Math.max(0, s); return (t / 60) + ":" + (t % 60 < 10 ? "0" : "") + (t % 60); }

    private TextView btn(String txt, int sp, final String action) {
        TextView v = new TextView(this);
        v.setText(txt); v.setTextColor(Color.WHITE); v.setTextSize(sp); v.setGravity(Gravity.CENTER);
        v.setPadding(28, 18, 28, 18);
        v.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { LocalMusicPlugin.emit(action, 0); } });
        return v;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cur = this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        bg = new ImageView(this); bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(this); dim.setBackgroundColor(0x88000000);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.BOTTOM);
        col.setPadding(48, 48, 48, 120);
        tTitle = new TextView(this); tTitle.setTextColor(Color.WHITE); tTitle.setTextSize(22);
        tTitle.setSingleLine(true); tTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tArtist = new TextView(this); tArtist.setTextColor(0xCCFFFFFF); tArtist.setTextSize(15); tArtist.setSingleLine(true);
        sb = new SeekBar(this); sb.setMax(1000);
        LinearLayout times = new LinearLayout(this);
        tCur = new TextView(this); tCur.setTextColor(0xCCFFFFFF); tCur.setTextSize(12);
        tDur = new TextView(this); tDur.setTextColor(0xCCFFFFFF); tDur.setTextSize(12); tDur.setGravity(Gravity.END);
        times.addView(tCur, new LinearLayout.LayoutParams(0, -2, 1)); times.addView(tDur, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER);
        bFav = btn("\u2661", 24, "fav"); bPlay = btn("\u25B6", 34, "playpause");
        row.addView(bFav); row.addView(btn("\u23EE", 26, "prev")); row.addView(btn("\u23EA", 22, "back"));
        row.addView(bPlay); row.addView(btn("\u23E9", 22, "fwd")); row.addView(btn("\u23ED", 26, "next"));
        col.addView(tTitle); col.addView(tArtist); col.addView(sb); col.addView(times); col.addView(row);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView x = new TextView(this); x.setText("\u2715"); x.setTextColor(Color.WHITE); x.setTextSize(22); x.setPadding(40, 60, 40, 40);
        x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(-2, -2); xp.gravity = Gravity.TOP | Gravity.END;
        root.addView(x, xp);
        setContentView(root);

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { if (u) tCur.setText(fmt(LocalMusicPlugin.NP_dur * p / 1000.0)); }
            public void onStartTrackingTouch(SeekBar s) { dragging = true; }
            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                double pos = LocalMusicPlugin.NP_dur * s.getProgress() / 1000.0;
                LocalMusicPlugin.NP_pos = pos; LocalMusicPlugin.NP_stamp = System.currentTimeMillis();
                LocalMusicPlugin.emit("seek", pos);
            }
        });
        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            public boolean onFling(MotionEvent a, MotionEvent b2, float vx, float vy) {
                if (a == null || b2 == null) return false;
                float dy = b2.getY() - a.getY();
                if (Math.abs(dy) > 160 && Math.abs(vy) > 900 && Math.abs(vy) > Math.abs(vx)) {
                    LocalMusicPlugin.emit(dy < 0 ? "next" : "prev", 0); return true;
                }
                return false;
            }
        });
        fill();
        h.post(tick);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) { if (gd != null) gd.onTouchEvent(ev); return super.dispatchTouchEvent(ev); }

    private void fill() {
        tTitle.setText(LocalMusicPlugin.NP_title); tTitle.setSelected(true);
        tArtist.setText(LocalMusicPlugin.NP_artist);
        bPlay.setText(LocalMusicPlugin.NP_playing ? "\u23F8" : "\u25B6");
        bFav.setText(LocalMusicPlugin.NP_fav ? "\u2665" : "\u2661");
        Bitmap c = LocalMusicPlugin.NP_cover;
        if (c != null) bg.setImageBitmap(c); else bg.setImageDrawable(null);
        updateTime();
    }

    private void updateTime() {
        double pos = LocalMusicPlugin.NP_pos;
        if (LocalMusicPlugin.NP_playing) pos += (System.currentTimeMillis() - LocalMusicPlugin.NP_stamp) / 1000.0;
        double d = LocalMusicPlugin.NP_dur;
        if (d > 0 && pos > d) pos = d;
        tDur.setText(fmt(d));
        if (!dragging) { tCur.setText(fmt(pos)); sb.setProgress(d > 0 ? (int) (pos / d * 1000) : 0); }
    }

    @Override protected void onDestroy() { h.removeCallbacks(tick); if (cur == this) cur = null; super.onDestroy(); }
}
