package com.aeterna.vitrix;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
public class MainActivity extends BridgeActivity {
  @Override public void onCreate(Bundle b) {
    registerPlugin(LocalMusicPlugin.class);
    super.onCreate(b);
  }
}
