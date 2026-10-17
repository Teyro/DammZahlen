#!/usr/bin/env python3
"""Oberflächentest im CI-Emulator (screenshots.yml): legt Material, zieht Plättchen ins
Zwanzigerfeld, setzt Kinder in den Bus, zeigt den Zehnerübergang 8 + 5, schiebt Kugeln am
Rechenrahmen, Blitzblick und Kindermodus – und sammelt Screenshots."""
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PAKET = "de.oejendorferdamm.dammzahlen"
ORDNER = "screenshots"


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True)


def screenshot(name):
    daten = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
    with open(os.path.join(ORDNER, name), "wb") as f:
        f.write(daten)
    print("Screenshot:", name)


def knoten(*namen):
    for _ in range(4):
        if adb("shell", "uiautomator", "dump", "/data/local/tmp/d.xml").returncode == 0 and \
                adb("pull", "/data/local/tmp/d.xml", "d.xml").returncode == 0:
            try:
                for k in ET.parse("d.xml").iter("node"):
                    if k.get("content-desc") in namen or k.get("text") in namen:
                        return [int(v) for v in k.get("bounds").replace("][", ",").strip("[]").split(",")]
                return None
            except ET.ParseError:
                pass
        time.sleep(1.5)
    return None


def tippe(*namen):
    z = knoten(*namen)
    if z is None:
        print("WARNUNG: nicht gefunden:", namen, file=sys.stderr)
        return False
    adb("shell", "input", "tap", str((z[0] + z[2]) // 2), str((z[1] + z[3]) // 2))
    return True


def bildschirm():
    zeilen = adb("shell", "wm", "size").stdout.splitlines()
    for z in sorted(zeilen, key=lambda z: "Override" not in z):
        if "x" in z:
            w, h = z.split()[-1].split("x")
            return max(int(w), int(h)), min(int(w), int(h))
    return 1920, 1080


class Welt:
    """Rechnet Vorbild-Pixel (1920 × 1080) in Bildschirmpixel um – wie die App."""

    def __init__(self):
        self.w, self.h = bildschirm()
        self.e = min(self.w / 1920, self.h / 1080)
        self.ox = (self.w - 1920 * self.e) / 2
        self.oy = (self.h - 1080 * self.e) / 2

    def p(self, x, y):
        return int(self.ox + x * self.e), int(self.oy + y * self.e)

    def tap(self, x, y):
        sx, sy = self.p(x, y)
        adb("shell", "input", "tap", str(sx), str(sy))

    def ziehe(self, x1, y1, x2, y2, ms=700):
        a = self.p(x1, y1)
        b = self.p(x2, y2)
        adb("shell", "input", "swipe", str(a[0]), str(a[1]), str(b[0]), str(b[1]), str(ms))


def platz_zwanziger(fx, fy, i):
    """Mitte von Platz i (0–19) im Zwanzigerfeld mit linker oberer Ecke (fx, fy)."""
    z, s = divmod(i, 10)
    return fx + 12 + s * 64 + (16 if s >= 5 else 0) + 32, fy + 12 + z * 64 + 32


def platz_bus(bx, by, i):
    deck = 194 if i < 10 else 60
    s = i % 10
    return bx + 190 + s * 64 + (16 if s >= 5 else 0) + 32, by + deck + 32


def laeuft():
    r = adb("shell", "pidof", PAKET)
    return r.returncode == 0 and r.stdout.strip() != ""


def main():
    os.makedirs(ORDNER, exist_ok=True)
    adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    adb("shell", "am", "start", "-n", f"{PAKET}/.MainActivity")
    time.sleep(5)
    if not laeuft():
        print("FEHLER: App läuft nicht", file=sys.stderr)
        sys.exit(1)
    welt = Welt()
    screenshot("01_start.png")

    # Zwanzigerfeld + Plättchen aus dem Vorrat hineinziehen
    tippe("Material")
    time.sleep(1.5)
    screenshot("02_material.png")
    tippe("Zwanzigerfeld")
    time.sleep(1.5)
    fx, fy = 620, 300
    for i in range(8):
        welt.ziehe(66, 290, *platz_zwanziger(fx, fy, i))
        time.sleep(0.4)
    for i in range(8, 13):
        welt.ziehe(66, 290, *platz_zwanziger(fx, fy, i))
        time.sleep(0.4)
    # die letzten fünf blau wenden
    for i in range(8, 13):
        welt.tap(*platz_zwanziger(fx, fy, i))
        time.sleep(0.3)
    time.sleep(1)
    screenshot("03_zwanzigerfeld_8_plus_5.png")

    # Bus mit Kindern: drei unten, zwei oben einsteigen lassen
    tippe("Neue Seite")
    time.sleep(1)
    tippe("Material")
    time.sleep(1.2)
    tippe("Bus mit 10 Kindern")
    time.sleep(1.5)
    bx, by = 520, 160
    for k, platz in enumerate([0, 1, 2, 10, 11]):
        welt.ziehe(560 + k * 62, 640, *platz_bus(bx, by, platz))
        time.sleep(0.5)
    time.sleep(1)
    screenshot("04_bus.png")

    # Zehnerübergang 8 + 5
    tippe("Zehnerübergang")
    time.sleep(1.5)
    screenshot("05_uebergang_fenster.png")
    tippe("8 + 5")
    time.sleep(0.5)
    tippe("Zeigen")
    time.sleep(1.5)
    screenshot("06_uebergang_schritt1.png")
    tippe("Weiter")
    time.sleep(1.5)
    screenshot("07_uebergang_schritt2.png")
    tippe("Weiter")
    time.sleep(1.5)
    screenshot("08_uebergang_schritt3.png")
    tippe("Fertig")
    time.sleep(1)

    # Hunderterübergang 38 + 7
    tippe("Zehnerübergang")
    time.sleep(1.5)
    tippe("38 + 7")
    time.sleep(0.5)
    tippe("Zeigen")
    time.sleep(1)
    tippe("Weiter")
    time.sleep(0.8)
    tippe("Weiter")
    time.sleep(1.5)
    screenshot("09_hunderteruebergang.png")
    tippe("Fertig")
    time.sleep(1)

    # Rechenrahmen und Zahlenstrahl auf neuer Seite
    tippe("Neue Seite")
    time.sleep(1)
    tippe("Material")
    time.sleep(1.2)
    tippe("Rechenrahmen 20")
    time.sleep(1)
    tippe("Material")
    time.sleep(1.2)
    tippe("Zahlenstrahl 0–20")
    time.sleep(1)
    # Rahmen liegt bei (600, 360): siebte Kugel der ersten Reihe antippen → 7 nach links
    kugel_x = 600 + 20 + 676 - 10 - (10 - 6) * 44 + 22
    welt.tap(kugel_x, 360 + 20 + 26)
    time.sleep(0.8)
    # Zahlenstrahl liegt bei (170, 640) – Marke auf 13
    welt.tap(170 + 40 + 1500 * 13 / 20, 640 + 90)
    time.sleep(1)
    screenshot("10_rahmen_und_strahl.png")

    # Blitzblick (auf dieser Seite liegt der Rechenrahmen)
    tippe("Blitzblick")
    time.sleep(1.2)
    tippe("2 Sek.")
    time.sleep(1.5)
    screenshot("11_blitzblick_zeigen.png")
    time.sleep(3)
    screenshot("12_blitzblick_zugedeckt.png")

    # Kindermodus
    tippe("Kindermodus")
    time.sleep(1.5)
    screenshot("13_kindermodus.png")
    tippe("Kindermodus beenden")
    time.sleep(1)

    if not laeuft():
        print("FEHLER: App ist abgestürzt", file=sys.stderr)
    with open("logcat.txt", "w") as f:
        subprocess.run(["adb", "logcat", "-d"], stdout=f)
    log = open("logcat.txt").read()
    if "FATAL EXCEPTION" in log and f"Process: {PAKET}," in log:
        print("FEHLER: Absturz im Logcat", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
