package com.aeterna.vitrix;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import java.io.ByteArrayOutputStream;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

@CapacitorPlugin(name = "LocalMusic", permissions = {
    @Permission(alias = "audio", strings = { Manifest.permission.READ_MEDIA_AUDIO }),
    @Permission(alias = "legacy", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
})
public class LocalMusicPlugin extends Plugin {

    private String alias() { return Build.VERSION.SDK_INT >= 33 ? "audio" : "legacy"; }

    @PluginMethod
    public void list(PluginCall call) {
        if (getPermissionState(alias()).toString().equals("granted")) { doList(call); return; }
        requestPermissionForAlias(alias(), call, "afterPerm");
    }

    @com.getcapacitor.annotation.PermissionCallback
    private void afterPerm(PluginCall call) {
        if (getPermissionState(alias()).toString().equals("granted")) doList(call);
        else call.reject("Permission denied");
    }

    private void doList(PluginCall call) {
        int minSec = call.getInt("minSeconds", 0);
        JSArray songs = new JSArray();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj = { MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DISPLAY_NAME };
        try (Cursor c = getContext().getContentResolver().query(uri, proj,
            MediaStore.Audio.Media.IS_MUSIC + "!=0 OR " + MediaStore.Audio.Media.DURATION + ">0", null,
            MediaStore.Audio.Media.DATA + " ASC")) {
            while (c != null && c.moveToNext()) {
                String path = c.getString(0);
                if (path == null) continue;
                double dur = c.getLong(4) / 1000.0;
                if (dur < minSec) continue;
                JSObject o = new JSObject();
                o.put("path", path);
                o.put("title", c.getString(1));
                String ar = c.getString(2); if (ar != null && !ar.equals("<unknown>")) o.put("artist", ar);
                String al = c.getString(3); if (al != null) o.put("album", al);
                o.put("duration", dur);
                o.put("name", c.getString(5));
                File f = new File(path);
                o.put("folder", f.getParentFile() != null ? f.getParentFile().getName() : "");
                songs.put(o);
            }
        } catch (Exception e) { call.reject(e.getMessage()); return; }
        JSObject r = new JSObject(); r.put("songs", songs); call.resolve(r);
    }


    private byte[] shrink(byte[] raw) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
            int ss = 1; while (o.outWidth / ss > 900 || o.outHeight / ss > 900) ss *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options(); d.inSampleSize = ss;
            Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length, d);
            if (b == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 88, out); b.recycle();
            return out.toByteArray();
        } catch (Throwable t) { return null; }
    }

    @PluginMethod
    public void art(PluginCall call) {
        String p = call.getString("path");
        if (p == null) { call.resolve(new JSObject()); return; }
        byte[] pic = null;
        try {
            MediaMetadataRetriever m = new MediaMetadataRetriever();
            m.setDataSource(p);
            byte[] e = m.getEmbeddedPicture();
            m.release();
            if (e != null) pic = shrink(e);
        } catch (Throwable t) { }
        if (pic == null) {
            File dir = new File(p).getParentFile();
            String[] names = { "cover", "folder", "front", "albumart", "album", "art" };
            String[] exts = { ".jpg", ".jpeg", ".png", ".webp" };
            if (dir != null) for (String n : names) { for (String x : exts) {
                File f = new File(dir, n + x);
                if (!f.exists()) continue;
                try {
                    java.io.FileInputStream in = new java.io.FileInputStream(f);
                    byte[] raw = new byte[(int) f.length()]; int off = 0, r;
                    while (off < raw.length && (r = in.read(raw, off, raw.length - off)) > 0) off += r;
                    in.close(); pic = shrink(raw);
                } catch (Throwable t) { }
                if (pic != null) break;
            } if (pic != null) break; }
        }
        JSObject r = new JSObject();
        if (pic != null) { r.put("data", Base64.encodeToString(pic, Base64.NO_WRAP)); r.put("type", "image/jpeg"); }
        call.resolve(r);
    }

    @PluginMethod
    public void hasAllFiles(PluginCall call) {
        boolean ok = Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager() : true;
        JSObject r = new JSObject(); r.put("value", ok); call.resolve(r);
    }

    @PluginMethod
    public void requestAllFiles(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            }
        } catch (Exception e) { openAppSettings(call); return; }
        call.resolve();
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void listStorage(PluginCall call) {
        String path = call.getString("path", "/storage/emulated/0");
        File dir = new File(path);
        File[] fs = dir.listFiles();
        ArrayList<File> all = new ArrayList<>();
        if (fs != null) all.addAll(Arrays.asList(fs));
        Collections.sort(all, new Comparator<File>() {
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        JSArray arr = new JSArray();
        int n = 0; boolean trunc = false;
        for (File f : all) {
            if (n >= 300) { trunc = true; break; }
            if (f.getName().startsWith(".")) continue;
            JSObject o = new JSObject();
            o.put("name", f.getName()); o.put("path", f.getAbsolutePath());
            o.put("directory", f.isDirectory()); o.put("size", f.length()); o.put("modified", f.lastModified());
            arr.put(o); n++;
        }
        JSObject r = new JSObject(); r.put("path", dir.getAbsolutePath()); r.put("entries", arr); r.put("truncated", trunc);
        call.resolve(r);
    }

    private void rescan(String... paths) {
        MediaScannerConnection.scanFile(getContext(), paths, null, null);
    }

    @PluginMethod
    public void deleteFile(PluginCall call) {
        String p = call.getString("path");
        if (p == null) { call.reject("No path"); return; }
        File f = new File(p);
        boolean ok = deleteRec(f);
        rescan(p);
        if (ok) call.resolve(); else call.reject("Could not delete (allow All files access)");
    }

    private boolean deleteRec(File f) {
        if (f.isDirectory()) { File[] k = f.listFiles(); if (k != null) for (File c : k) deleteRec(c); }
        return f.delete();
    }

    @PluginMethod
    public void writeFile(PluginCall call) {
        String p = call.getString("path"), data = call.getString("data");
        boolean keep = call.getBoolean("keepTime", false);
        if (p == null || data == null) { call.reject("Bad args"); return; }
        try {
            File f = new File(p); long t = f.exists() ? f.lastModified() : 0;
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(Base64.decode(data, Base64.DEFAULT)); }
            if (keep && t > 0) f.setLastModified(t);
            rescan(p); call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    @PluginMethod
    public void latestImage(PluginCall call) {
        long since = (long) (call.getDouble("since", 0.0) / 1000);
        File[] fs = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()).listFiles();
        File best = null;
        if (fs != null) for (File f : fs) {
            String n = f.getName().toLowerCase();
            if (!(n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))) continue;
            if (f.lastModified() / 1000 < since) continue;
            if (best == null || f.lastModified() > best.lastModified()) best = f;
        }
        JSObject r = new JSObject(); if (best != null) r.put("path", best.getAbsolutePath()); call.resolve(r);
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(call.getString("url")));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i); call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    /* ---------- lock-screen player ---------- */
    static String NP_title = "", NP_artist = "", NP_album = "";
    static double NP_dur = 0, NP_pos = 0;
    static boolean NP_playing = false, NP_fav = false;
    static long NP_stamp = 0, NP_pausedSince = 0;
    static Bitmap NP_cover = null;
    static boolean lockEnabled = false;
    static LocalMusicPlugin inst;
    private BroadcastReceiver screenRcv;

    @Override
    public void load() {
        inst = this;
        screenRcv = new BroadcastReceiver() {
            public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(a)) maybeShow(c);
                else if (Intent.ACTION_USER_PRESENT.equals(a)) LockActivity.closeIt();
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        getContext().registerReceiver(screenRcv, f);
    }

    private void maybeShow(Context c) {
        try {
            if (!lockEnabled || NP_title == null || NP_title.isEmpty()) return;
            if (!NP_playing && System.currentTimeMillis() - NP_pausedSince > 60000) return;
            KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
            if (km == null || !km.isKeyguardLocked()) return;
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(c)) return;
            Intent i = new Intent(c, LockActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            c.startActivity(i);
        } catch (Throwable t) { }
    }

    static void emit(String action, double pos) {
        if (inst == null) return;
        JSObject o = new JSObject(); o.put("action", action); o.put("pos", pos);
        inst.notifyListeners("lockAction", o);
    }

    @PluginMethod
    public void setLockEnabled(PluginCall c) {
        lockEnabled = c.getBoolean("enabled", false);
        c.resolve();
    }

    @PluginMethod
    public void setNowPlaying(PluginCall c) {
        boolean wasPlaying = NP_playing;
        NP_title = c.getString("title", ""); NP_artist = c.getString("artist", ""); NP_album = c.getString("album", "");
        NP_dur = c.getDouble("duration", 0.0); NP_pos = c.getDouble("position", 0.0);
        NP_playing = c.getBoolean("playing", false); NP_fav = c.getBoolean("fav", false);
        NP_stamp = System.currentTimeMillis();
        if (wasPlaying && !NP_playing) NP_pausedSince = NP_stamp;
        if (c.getBoolean("coverSet", false)) {
            NP_cover = null;
            String b64 = c.getString("cover", "");
            if (b64 != null && !b64.isEmpty()) {
                try {
                    byte[] raw = Base64.decode(b64, Base64.DEFAULT);
                    NP_cover = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                } catch (Throwable t) { }
            }
        }
        LockActivity.refresh();
        c.resolve();
    }

    @PluginMethod
    public void canOverlay(PluginCall c) {
        JSObject r = new JSObject();
        r.put("value", Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(getContext()));
        c.resolve(r);
    }

    @PluginMethod
    public void requestOverlay(PluginCall c) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Throwable t) { }
        c.resolve();
    }
}
