package com.aeterna.vitrix;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
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

    /* lock-screen overlay not used: the system media notification already shows on the lock screen */
    @PluginMethod public void setLockEnabled(PluginCall c) { c.resolve(); }
    @PluginMethod public void setNowPlaying(PluginCall c) { c.resolve(); }
    @PluginMethod public void requestOverlay(PluginCall c) { c.resolve(); }
    @PluginMethod public void canOverlay(PluginCall c) { JSObject r = new JSObject(); r.put("value", false); c.resolve(r); }
}
