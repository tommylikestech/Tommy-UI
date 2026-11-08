"""Windows USB updater for the extracted Tommy UI kit. Requires Python 3.11+ and platform-tools."""
import hashlib
import json
import queue
import shutil
import subprocess
import sys
import threading
import time
import tkinter as tk
from pathlib import Path
from tkinter import messagebox, ttk

FOLDER = Path(__file__).resolve().parent
INFO = json.loads((FOLDER / "Tommy-UI-v1.003-build.json").read_text())
EXPECTED = INFO["build_number"]
BASE = "TommyUI.v1.002.dev.20261106"

def run(*args, timeout=20):
    proc = subprocess.run(args, capture_output=True, text=True, timeout=timeout,
                          creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    if proc.returncode:
        raise RuntimeError((proc.stderr or proc.stdout).strip())
    return proc.stdout.strip()

def devices(tool):
    lines=run(tool,"devices").splitlines()
    return [line.split() for line in lines if line.strip() and not line.startswith(("List of","*"))]

def inspect():
    if not shutil.which("adb") or not shutil.which("fastboot"):
        raise RuntimeError("Add Android platform-tools to PATH and reopen the updater.")
    connected=devices("adb")
    if len(connected)!=1 or len(connected[0]) != 2 or connected[0][1]!="device":
        raise RuntimeError("Connect one phone in Android, enable USB debugging and accept the PC prompt.")
    serial=connected[0][0]
    def prop(key):
        return run("adb","-s",serial,"shell","getprop",key)
    if prop("ro.product.vendor.device")!="fogona" or prop("ro.boot.hardware.sku")!="XT2413-2":
        raise RuntimeError("This kit is for the Moto G Play 2024 XT2413-2.")
    return serial,prop("ro.build.version.incremental")

def install(serial, note):
    current,version=inspect()
    if current!=serial or version!=BASE:
        raise RuntimeError("The device or base version changed. Check again.")
    image=FOLDER/INFO["image"]
    with image.open("rb") as src:
        if hashlib.file_digest(src,"sha256").hexdigest()!=INFO["image_sha256"]:
            raise RuntimeError("Image checksum mismatch. Extract the complete kit again.")
    note("Restarting the phone into fastbootd…")
    run("adb","-s",serial,"reboot","fastboot")
    for _ in range(60):
        if devices("fastboot")==[[serial,"fastboot"]]: break
        time.sleep(1)
    else: raise RuntimeError("Fastbootd did not connect. Check the cable and Windows driver.")
    note("Verifying partitions and writing system_a. Keep USB connected.")
    run(sys.executable,str(FOLDER/"flash_later.py"),"--flash","--serial",serial,timeout=700)
    note("Write succeeded. Waiting for Android…")
    for _ in range(60):
        try:
            current,version=inspect()
            if current==serial and version==EXPECTED:
                return "Update verified on your phone: Tommy UI v1.003."
        except (RuntimeError,subprocess.TimeoutExpired): pass
        time.sleep(1)
    return "Flash succeeded. Unlock the phone and check Settings → About Tommy UI to confirm its version."

def main():
    window=tk.Tk(); window.title("Tommy USB Updater")
    box=ttk.Frame(window,padding=24);box.pack(fill="both",expand=True)
    ttk.Label(box,text="Tommy UI v1.003",font=("Segoe UI",18)).pack(anchor="w")
    status=tk.StringVar(value="Connect your Moto G Play 2024 with USB debugging enabled.")
    ttk.Label(box,textvariable=status,wraplength=380).pack(anchor="w",pady=14)
    events=queue.Queue()
    def worker(task):
        try: events.put(("done",task()))
        except Exception as e: events.put(("error",str(e)))
    def start(task):
        button.state(["disabled"])
        threading.Thread(target=worker,args=(task,),daemon=True).start()
    def check(): start(inspect)
    button=ttk.Button(box,text="Check connected phone",command=check);button.pack(anchor="w")
    def poll():
        while not events.empty():
            kind,value=events.get()
            if kind=="note":status.set(value);continue
            button.state(["!disabled"])
            if isinstance(value,tuple):
                serial,version=value
                status.set("Installed build: "+version)
                if version==BASE and messagebox.askyesno("Install Tommy UI v1.003?",
                        "Update this phone from v1.002 to v1.003? The updater writes the system partition and reboots the phone."):
                    start(lambda:install(serial,lambda s:events.put(("note",s))))
                elif version==EXPECTED:status.set("Your phone already has Tommy UI v1.003.")
                elif version!=BASE:
                    status.set("This kit updates Tommy UI v1.002. The connected phone runs " + version + ".")
            else:status.set(value)
        window.after(200,poll)
    window.protocol("WM_DELETE_WINDOW",lambda:None if button.instate(["disabled"]) else window.destroy())
    poll();window.mainloop()

if __name__=="__main__":
    main()
