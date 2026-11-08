"""Run the actual v1.002 volume policy on a host JVM with a simulated phone/card inventory."""
import argparse
from pathlib import Path
import subprocess
import tempfile

PROJECT = Path(__file__).resolve().parents[1]
POLICY = PROJECT / "overlay/frameworks/base/packages/PackageInstaller/src/com/android/packageinstaller/InstallDestinations.java"

STUBS = {
    "android.content.pm.PackageInfo": "public static final int INSTALL_LOCATION_AUTO=0, INSTALL_LOCATION_PREFER_EXTERNAL=2;",
    "android.content.pm.PackageManager": """
        public static final int MATCH_ANY_USER=1, MATCH_UNINSTALLED_PACKAGES=2, INSTALL_INTERNAL=16, INSTALL_FORCE_VOLUME_UUID=512;
        public static class NameNotFoundException extends Exception {}
        public boolean installed;
        public Object getApplicationInfo(String pkg, int flags) throws NameNotFoundException {
            if (installed && flags==3) return new Object(); throw new NameNotFoundException();
        }""",
    "android.content.pm.PackageInstaller": """
        public static class SessionParams { public String appPackageName,volumeUuid;
            public long sizeBytes; public int installLocation,installFlags; }""",
    "android.content.Context": """
        public final android.content.pm.PackageManager pm=new android.content.pm.PackageManager();
        public final android.os.storage.StorageManager storage=new android.os.storage.StorageManager();
        public android.content.pm.PackageManager getPackageManager(){return pm;}
        public <T> T getSystemService(Class<T> type){return type.cast(storage);}
        public Object getContentResolver(){return this;}
        public Context getResources(){return this;}
        public boolean getBoolean(int id){return true;}""",
    "android.os.storage.VolumeInfo": """
        public static final int TYPE_PRIVATE=1; public static final String ID_PRIVATE_INTERNAL="private";
        public String id,uuid; public boolean writable=true,sd; public int type=1;
        public VolumeInfo(String id, String uuid, boolean sd){this.id=id;this.uuid=uuid;this.sd=sd;}
        public boolean isMountedWritable(){return writable;} public int getType(){return type;}
        public String getId(){return id;} public String getFsUuid(){return uuid;}
        public java.io.File getPath(){return new java.io.File(id);}
        public VolumeInfo getDisk(){return uuid==null ? null : this;} public boolean isSd(){return sd;}""",
    "android.os.storage.StorageManager": """
        public java.util.List<VolumeInfo> volumes=new java.util.ArrayList<>();
        public java.util.Map<java.io.File,Long> space=new java.util.HashMap<>();
        public java.util.List<VolumeInfo> getVolumes(){return volumes;}
        public String getBestVolumeDescription(VolumeInfo v){return v.getId();}
        public java.io.File getUuidForPath(java.io.File f) throws java.io.IOException{return f;}
        public long getAllocatableBytes(java.io.File f){return space.getOrDefault(f,1000L);}""",
    "android.provider.Settings": """
        public static class Global { public static final String FORCE_ALLOW_ON_EXTERNAL="force";
          public static int force; public static int getInt(Object c,String k,int d){return force;} }""",
    "android.text.format.Formatter": "public static String formatFileSize(Object context, long bytes){return Long.toString(bytes);}",
    "com.android.internal.R": "public static class bool{public static final int config_allow3rdPartyAppOnInternal=1;}",
}

HARNESS = """
package com.android.packageinstaller;
import android.content.Context;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.os.storage.VolumeInfo;
import android.provider.Settings;
import java.io.IOException;
import java.util.List;

public class PolicyScenarios {
    static void check(boolean ok, String reason){if(!ok)throw new AssertionError(reason);}
    static void rejects(Context c,SessionParams p,InstallDestinations.Option picked)throws Exception{
        try { InstallDestinations.apply(c,p,picked); throw new AssertionError("accepted stale choice"); }
        catch(IOException expected) { check(p.installFlags==0 && p.volumeUuid==null,"partial selection"); }
    }
    public static void main(String[] args) throws Exception {
        Context c=new Context();
        SessionParams p=new SessionParams(); p.appPackageName="example.newapp";p.sizeBytes=100;
        VolumeInfo phone=new VolumeInfo("private",null,false),sd=new VolumeInfo("private:1","cardA",true);
        VolumeInfo usb=new VolumeInfo("usb","usb",false),portable=new VolumeInfo("public","portable",true);
        portable.type=0;
        c.storage.volumes.addAll(List.of(usb,portable,phone));
        check(InstallDestinations.list(c,p).isEmpty(),"offered USB or portable storage");
        c.storage.volumes.add(sd);
        var choices=InstallDestinations.list(c,p);
        check(choices.size()==2 && choices.get(0).uuid==null && choices.get(1).uuid.equals("cardA"),"destinations");
        InstallDestinations.apply(c,p,choices.get(1));
        check(p.volumeUuid.equals("cardA") && p.installFlags==PackageManager.INSTALL_FORCE_VOLUME_UUID,"card placement");
        p.installFlags=0;p.volumeUuid=null;
        InstallDestinations.apply(c,p,choices.get(0));
        check(p.volumeUuid==null && p.installFlags==PackageManager.INSTALL_INTERNAL,"phone placement");
        p.installFlags=0;
        c.pm.installed=true;check(InstallDestinations.list(c,p).isEmpty(),"offered a move on update");
        rejects(c,p,choices.get(1));c.pm.installed=false;
        p.installLocation=1;check(InstallDestinations.list(c,p).isEmpty(),"ignored internalOnly");
        p.installLocation=-1;check(InstallDestinations.list(c,p).isEmpty(),"ignored unspecified preference");
        Settings.Global.force=1;check(InstallDestinations.list(c,p).size()==2,"force override");
        sd.writable=false;rejects(c,p,choices.get(1));sd.writable=true;
        sd.uuid="replacement";rejects(c,p,choices.get(1));sd.uuid="cardA";
        p.appPackageName="changed";rejects(c,p,choices.get(1));p.appPackageName="example.newapp";
        c.storage.space.put(sd.getPath(),99L);rejects(c,p,choices.get(1));
        System.out.println("Passed fresh install, update, force preference, phone/SD selection, portable/USB exclusion, card removal/replacement, package change and insufficient-space scenarios.");
    }
}
"""

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jdk", type=Path)
    tools = parser.parse_args().jdk / "bin"
    with tempfile.TemporaryDirectory(prefix="tommy-install-policy-") as directory:
        temp = Path(directory)
        sources = [POLICY]
        for full_name, body in STUBS.items():
            package, name = full_name.rsplit(".", 1)
            path = temp / package.replace(".", "/") / (name + ".java")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f"package {package}; public class {name} {{{body}}}")
            sources.append(path)
        harness = temp / "PolicyScenarios.java"
        harness.write_text(HARNESS)
        subprocess.run([tools / "javac", "-d", temp, *sources, harness], check=True)
        subprocess.run([tools / "java", "-cp", temp, "com.android.packageinstaller.PolicyScenarios"], check=True)

if __name__ == "__main__":
    main()
