import re,glob,shutil,os
m='android/app/src/main/AndroidManifest.xml'
s=open(m).read()
perms=''.join('<uses-permission android:name="android.permission.%s"/>\n'%p for p in ['READ_MEDIA_AUDIO','READ_MEDIA_VIDEO','READ_MEDIA_IMAGES','READ_EXTERNAL_STORAGE','WRITE_EXTERNAL_STORAGE','MANAGE_EXTERNAL_STORAGE','FOREGROUND_SERVICE','FOREGROUND_SERVICE_MEDIA_PLAYBACK','POST_NOTIFICATIONS','WAKE_LOCK','SYSTEM_ALERT_WINDOW'])
s=s.replace('<application',perms+'<application android:requestLegacyExternalStorage="true"',1)
act='<activity android:name=".LockActivity" android:exported="false" android:showWhenLocked="true" android:turnScreenOn="true" android:launchMode="singleTask" android:excludeFromRecents="true" android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen"/>\n'
s=s.replace('</application>',act+'</application>',1)
open(m,'w').write(s)
res='android/app/src/main/res'
for d in glob.glob(res+'/mipmap-anydpi*'): shutil.rmtree(d)
for d in glob.glob('resources/mipmap-*'):
    dst=res+'/'+os.path.basename(d); os.makedirs(dst,exist_ok=True)
    for f in glob.glob(d+'/*.png'): shutil.copy(f,dst)
open(res+'/values/bgdark.xml','w').write('<resources><color name="bgdark">#0a0c0f</color></resources>')
st=res+'/values/styles.xml'
t=open(st).read().replace('@drawable/splash','@color/bgdark')
t=re.sub(r'(<style name="AppTheme.NoActionBar"[^>]*>)',r'\1<item name="android:windowBackground">@color/bgdark</item>',t)
open(st,'w').write(t)
g='android/app/build.gradle'
b=open(g).read()
sign='signingConfigs {\n debug {\n storeFile file("../../native/aeterna.keystore")\n storePassword "aeterna123"\n keyAlias "aeterna"\n keyPassword "aeterna123"\n }\n }\n'
b=re.sub(r'android\s*\{',lambda m:'android {\n'+sign,b,count=1)
open(g,'w').write(b)
