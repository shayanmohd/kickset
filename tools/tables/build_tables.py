#!/usr/bin/env python3
"""Build Kickset's bundled table JSON from independently transcribed catalogue columns.

Each list below is transcribed from one published catalogue only. A value reaches the app only when
two catalogues agree (inch values within 0.007 in, which absorbs two-decimal rounding of a 1/16 or
1/32 fraction). A disagreement is resolved only by a third catalogue that agrees with one of them;
otherwise the value is dropped. The script prints every drop and every third-source resolution.
Run: python3 tools/tables/build_tables.py   (writes app/src/main/assets/tables/*.json)
"""
import json, os, sys
from fractions import Fraction

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "tables")
TOL = 0.007
log = []

def f(s):
    """'1 5/8' or '0.62' or None -> float."""
    if s is None: return None
    s = str(s).strip()
    if " " in s:
        w, fr = s.split(); return float(int(w) + Fraction(fr))
    if "/" in s: return float(Fraction(s))
    return float(s)

def snap(x, denom):
    """Nearest multiple of 1/denom; asserts the catalogue decimal is a rounded fraction."""
    q = round(x * denom) / denom
    assert abs(q - x) <= TOL, (x, q)
    return q

def agree(a, b):
    return a is not None and b is not None and abs(a - b) <= TOL

def merge(label, sizes, a, b, c=None, denom=16):
    rows = []
    for i, nps in enumerate(sizes):
        va, vb = f(a[i]), f(b[i])
        vc = f(c[i]) if c else None
        if agree(va, vb):
            v = va if isinstance(a[i], str) and "/" in a[i] else vb if isinstance(b[i], str) and "/" in b[i] else va
            rows.append((nps, snap(v, denom)))
        elif va is None and vb is None:
            continue
        elif vc is not None and (agree(va, vc) or agree(vb, vc)):
            v = va if agree(va, vc) else vb
            log.append(f"{label} NPS {nps}: sources disagree ({a[i]} vs {b[i]}), third source {c[i]} confirms {v}")
            rows.append((nps, snap(v, denom)))
        else:
            log.append(f"{label} NPS {nps}: dropped, only one source or disagreement ({a[i]} vs {b[i]})")
    return rows

BW_SIZES = ["1/2","3/4","1","1 1/4","1 1/2","2","2 1/2","3","3 1/2","4","5","6","8","10","12","14","16","18","20","22","24"]
N = None
# Weldbend Corporation, Carbon Steel Weld Fitting and Weld Flange Catalog, 63rd edition (c) 2010, schedule STD tables.
WB_90LR = ["1.50","1.50","1.50","1.88","2.25","3.00","3.75","4.50","5.25","6.00","7.50","9.00","12.00","15.00","18.00","21.00","24.00","27.00","30.00",N,"36.00"]   # p.26
WB_90SR = [N,N,"1.00","1.25","1.50","2.00","2.50","3.00","3.50","4.00","5.00","6.00","8.00","10.00","12.00","14.00","16.00","18.00","20.00",N,"24.00"]               # p.29 to 37
WB_45LR = ["0.62","0.75","0.88","1.00","1.12","1.38","1.75","2.00","2.25","2.50","3.12","3.75","5.00","6.25","7.50","8.75","10.00","11.25","12.50",N,"15.00"]      # p.38
WB_TEE  = ["1.00","1.12","1.50","1.88","2.25","2.50","3.00","3.38","3.75","4.12","4.88","5.62","7.00","8.50","10.00","11.00","12.00","13.50","15.00",N,"17.00"]  # p.49
WB_RED  = [N,"1.50","2.00","2.00","2.50","3.00","3.50","3.50","4.00","4.00","5.00","5.50","6.00","7.00","8.00","13.00","14.00","15.00","20.00",N,"20.00"]          # p.62 to 68, length L by large end
# Hackney Ladish, Inc., Dimension Data (documents/AllData.pdf), Straight Fitting Dimensions and Reducing Fittings.
HL_90LR = ["1.50","1.50","1.50","1.88","2.25","3.00","3.75","4.50","5.25","6.00","7.50","9.00","12.00","15.00","18.00","21.00","24.00","27.00","30.00","33.00","36.00"]
HL_90SR = [N,N,"1.00","1.25","1.50","2.00","2.50","3.00","3.50","4.00","5.00","6.00","8.00","10.00","12.00","14.00","16.00","18.00","20.00","22.00","24.00"]
HL_45LR = ["0.62","0.75","0.88","1.00","1.12","1.38","1.75","2.00","2.25","2.50","3.12","3.75","5.00","6.25","7.50","8.75","10.00","11.25","12.50","13.50","15.00"]
HL_TEE  = ["1.00","1.12","1.50","1.88","2.25","2.50","3.00","3.38","3.75","4.12","4.88","5.62","7.00","8.50","10.00","11.00","12.00","13.50","15.00","16.50","17.00"]
HL_RED  = [N,"1.5","2","2","2.5","3","3.5","3.5","4","4","5","5.5","6","7","8","13","14","15","20","20","20"]

SW_SIZES = ["1/8","1/4","3/8","1/2","3/4","1","1 1/4","1 1/2","2","2 1/2","3","4"]
# Bonney Forge, Forged Steel Fittings and Unions catalogue, Class 3000 socket weld tables (pages 6, 7, 8, 14).
BF_90  = ["7/16","7/16","17/32","5/8","3/4","7/8","1 1/16","1 1/4","1 1/2","1 5/8","2 1/4","2 5/8"]
BF_45  = ["5/16","5/16","5/16","7/16","1/2","9/16","11/16","13/16","1","1 1/8","1 1/4","1 5/8"]
BF_TEE = ["7/16","7/16","17/32","5/8","3/4","7/8","1 1/16","1 1/4","1 1/2","1 5/8","2 1/4","2 5/8"]
BF_CPL = ["1/4","1/4","1/4","3/8","3/8","1/2","1/2","1/2","3/4","3/4","3/4","3/4"]
# Anvil International, Pipe Fittings catalogue, Forged Steel Fittings, Class 3000 Socket Weld, figures 2150, 2151, 2152, 2154.
AN_90  = ["0.44","0.44","0.53","0.62","0.75","0.88","1.06","1.25","1.50","1.62","2.25","2.62"]
AN_45  = ["0.31","0.31","0.31","0.44","0.50","0.56","0.69","0.81","1.00","1.12","1.25","1.62"]
AN_TEE = ["0.44","0.44","0.53","0.62","0.75","0.88","1.06","1.25","1.50","1.62","2.25","2.62"]
AN_CPL = ["0.25","0.25","0.25","0.38","0.38","0.50","0.50","0.50","0.75","0.75","0.75","0.75"]

# Flanges ASME B16.5. Order: nps, bolts, bolt diameter, bolt circle, stud length RF.
FL_SIZES = ["1/2","3/4","1","1 1/4","1 1/2","2","2 1/2","3","3 1/2","4","5","6","8","10","12","14","16","18","20","24"]
# Weldbend catalogue p.86 (Class 150) and p.88 (Class 300).
WB150 = dict(n=[4,4,4,4,4,4,4,4,8,8,8,8,8,12,12,12,16,16,20,20],
  d=["1/2","1/2","1/2","1/2","1/2","5/8","5/8","5/8","5/8","5/8","3/4","3/4","3/4","7/8","7/8","1","1","1 1/8","1 1/8","1 1/4"],
  bc=["2.38","2.75","3.12","3.50","3.88","4.75","5.50","6.00","7.00","7.50","8.50","9.50","11.75","14.25","17.00","18.75","21.25","22.75","25.00","29.50"],
  L=["2.25","2.50","2.50","2.75","2.75","3.25","3.50","3.50","3.50","3.50","3.75","4.00","4.25","4.50","4.75","5.25","5.25","5.75","6.25","6.75"])
WB300 = dict(n=[4,4,4,4,4,8,8,8,8,8,8,12,12,16,16,20,20,24,24,24],
  d=["1/2","5/8","5/8","5/8","3/4","5/8","3/4","3/4","3/4","3/4","3/4","3/4","7/8","1","1 1/8","1 1/8","1 1/4","1 1/4","1 1/4","1 1/2"],
  bc=["2.62","3.25","3.50","3.88","4.50","5.00","5.88","6.62","7.25","7.88","9.25","10.62","13.00","15.25","17.75","20.25","22.50","24.75","27.00","32.00"],
  L=["2.50","3.00","3.00","3.25","3.50","3.50","4.00","4.25","4.25","4.50","4.75","4.75","5.50","6.25","6.75","7.00","7.50","7.75","8.00","9.00"])
# USA Fastener (MW Components), Bolt Reference Charts for ASME B16.5 Flanges.
UF150 = dict(n=[4,4,4,4,4,4,4,4,8,8,8,8,8,12,12,12,16,16,20,20],
  d=["1/2","1/2","1/2","1/2","1/2","5/8","5/8","5/8","5/8","5/8","3/4","3/4","3/4","7/8","7/8","1","1","1 1/8","1 1/8","1 1/4"],
  bc=["2 3/8","2 3/4","3 1/8","3 1/2","3 7/8","4 3/4","5 1/2","6","7","7 1/2","8 1/2","9 1/2","11 3/4","14 3/4","17","18 3/4","21 1/4","22 3/4","25","29 1/2"],
  L=["2 1/4","2 1/2","2 1/2","2 3/4","2 3/4","3 1/4","3 1/2","3 1/2","3 1/2","3 1/2","3 3/4","4","4 1/4","4 1/2","4 3/4","5 1/4","5 1/4","5 3/4","6 1/4","6 3/4"])
UF300 = dict(n=[4,4,4,4,4,8,8,8,8,8,8,12,12,16,16,20,20,24,24,24],
  d=["1/2","5/8","5/8","5/8","3/4","5/8","3/4","3/4","3/4","3/4","3/4","3/4","7/8","1","1 1/8","1 1/8","1 1/4","1 1/4","1 1/4","1 1/2"],
  bc=["2 5/8","3 1/4","3 1/2","3 7/8","4 1/2","5","5 7/8","6 5/8","7 1/4","7 7/8","9 1/4","10 5/8","13","15 1/4","17 3/4","20 1/4","20 1/2","24 3/4","27","32"],
  L=["2 1/2","3","3","3 1/4","3 1/2","3 1/2","4","4 1/4","4 1/4","4 1/2","4 3/4","4 3/4","5 1/2","6 1/4","6 3/4","7","7 1/2","7 3/4","8","9"])
# Coastal Flange, online catalogue, ANSI B16.5 Class 150 and Class 300 welding neck flanges (bolt circle and hole count).
CF150 = dict(n=[4,4,4,4,4,4,4,4,8,8,8,8,8,12,12,12,16,16,20,20],
  bc=["2.38","2.75","3.13","3.50","3.88","4.75","5.50","6.00","7.00","7.50","8.50","9.50","11.75","14.25","17.00","18.75","21.25","22.75","25.00","29.50"])
CF300 = dict(n=[4,4,4,4,4,8,8,8,8,8,8,12,12,16,16,20,20,24,24,24],
  bc=["2.63","3.25","3.50","3.88","4.50","5.00","5.88","6.63","7.25","7.88","9.25","10.63","13.00","15.25","17.75","20.25","22.50","24.75","27.00","32.00"])

# Pipe walls, inches. Schedules shown in the app: 10 20 30 40 STD 60 XS 80 100 120 140 160 XXS.
PIPE_SIZES = ["1/2","3/4","1","1 1/4","1 1/2","2","2 1/2","3","3 1/2","4","5","6","8","10","12","14","16","18","20","24"]
PIPE_OD = [0.840,1.050,1.315,1.660,1.900,2.375,2.875,3.500,4.000,4.500,5.563,6.625,8.625,10.750,12.750,14.000,16.000,18.000,20.000,24.000]
# Weldbend catalogue, Dimensions of Steel Pipe, p.138 to 143. OD cross-checked against Hackney Ladish column "Outside Diameter".
HL_OD = [0.84,1.05,1.315,1.66,1.9,2.375,2.875,3.5,4,4.5,5.562,6.625,8.625,10.75,12.75,14,16,18,20,24]
WB_PIPE = {
 "1/2": {"10":.083,"STD":.109,"40":.109,"XS":.147,"80":.147,"160":.188,"XXS":.294},
 "3/4": {"10":.083,"STD":.113,"40":.113,"XS":.154,"80":.154,"160":.219,"XXS":.308},
 "1": {"10":.109,"STD":.133,"40":.133,"XS":.179,"80":.179,"160":.250,"XXS":.358},
 "1 1/4": {"10":.109,"STD":.140,"40":.140,"XS":.191,"80":.191,"160":.250,"XXS":.382},
 "1 1/2": {"10":.109,"STD":.145,"40":.145,"XS":.200,"80":.200,"160":.281,"XXS":.400},
 "2": {"10":.109,"STD":.154,"40":.154,"XS":.218,"80":.218,"160":.344,"XXS":.436},
 "2 1/2": {"10":.120,"STD":.203,"40":.203,"XS":.276,"80":.276,"160":.375,"XXS":.552},
 "3": {"10":.120,"STD":.216,"40":.216,"XS":.300,"80":.300,"160":.438,"XXS":.600},
 "3 1/2": {"10":.120,"STD":.226,"40":.226,"XS":.318,"80":.318},
 "4": {"10":.120,"STD":.237,"40":.237,"XS":.337,"80":.337,"120":.438,"160":.531,"XXS":.674},
 "5": {"10":.134,"STD":.258,"40":.258,"XS":.375,"80":.375,"120":.500,"160":.625,"XXS":.750},
 "6": {"10":.134,"STD":.280,"40":.280,"XS":.432,"80":.432,"120":.562,"160":.719,"XXS":.864},
 "8": {"10":.148,"20":.250,"30":.277,"STD":.322,"40":.322,"60":.406,"XS":.500,"80":.500,"100":.594,"120":.719,"140":.812,"XXS":.875,"160":.906},
 "10": {"10":.165,"20":.250,"30":.307,"STD":.365,"40":.365,"XS":.500,"60":.500,"80":.594,"100":.719,"120":.844,"XXS":1.000,"140":1.000,"160":1.125},
 "12": {"10":.180,"20":.250,"30":.330,"STD":.375,"40":.406,"XS":.500,"60":.562,"80":.688,"100":.844,"XXS":1.000,"120":1.000,"140":1.125,"160":1.312},
 "14": {"10":.250,"20":.312,"STD":.375,"30":.375,"40":.438,"XS":.500,"60":.594,"80":.750,"100":.938,"120":1.094,"140":1.250,"160":1.406},
 "16": {"10":.250,"20":.312,"STD":.375,"30":.375,"XS":.500,"40":.500,"60":.656,"80":.844,"100":1.031,"120":1.219,"140":1.438,"160":1.594},
 "18": {"10":.250,"20":.312,"STD":.375,"30":.438,"XS":.500,"40":.562,"60":.750,"80":.938,"100":1.156,"120":1.375,"140":1.562,"160":1.781},
 "20": {"10":.250,"STD":.375,"20":.375,"XS":.500,"30":.500,"40":.594,"60":.812,"80":1.031,"100":1.281,"120":1.500,"140":1.750,"160":1.969},
 "24": {"10":.250,"STD":.375,"20":.375,"XS":.500,"30":.562,"40":.688,"60":.969,"80":1.219,"100":1.531,"120":1.812,"140":2.062,"160":2.344},
}
# Hackney Ladish, Common Diameters and Wall Thicknesses (AllData.pdf p.4). "STD"/"XS" cells in schedule columns resolved to those walls.
HL_PIPE = {
 "1/2": {"STD":.109,"XS":.147,"XXS":.294,"40":.109,"80":.147,"160":.188},
 "3/4": {"STD":.113,"XS":.154,"XXS":.308,"40":.113,"80":.154,"160":.219},
 "1": {"STD":.133,"XS":.179,"XXS":.358,"40":.133,"80":.179,"160":.25},
 "1 1/4": {"STD":.14,"XS":.191,"XXS":.382,"40":.14,"80":.191,"160":.25},
 "1 1/2": {"STD":.145,"XS":.2,"XXS":.4,"40":.145,"80":.2,"160":.281},
 "2": {"STD":.154,"XS":.218,"XXS":.438,"40":.154,"80":.218,"160":.344},
 "2 1/2": {"STD":.203,"XS":.276,"XXS":.552,"40":.203,"80":.276,"160":.375},
 "3": {"STD":.216,"XS":.3,"XXS":.6,"40":.216,"80":.3,"160":.438},
 "3 1/2": {"STD":.226,"XS":.318,"XXS":.636,"40":.226,"80":.318},
 "4": {"STD":.237,"XS":.337,"XXS":.674,"40":.237,"80":.337,"120":.438,"160":.531},
 "5": {"STD":.258,"XS":.375,"XXS":.75,"40":.258,"80":.375,"120":.5,"160":.625},
 "6": {"STD":.28,"XS":.432,"XXS":.864,"40":.28,"80":.432,"120":.562,"160":.719},
 "8": {"STD":.322,"XS":.5,"XXS":.875,"20":.25,"30":.277,"40":.322,"60":.406,"80":.5,"100":.594,"120":.719,"140":.812,"160":.906},
 "10": {"STD":.365,"XS":.5,"XXS":1,"20":.25,"30":.307,"40":.365,"60":.5,"80":.594,"100":.719,"120":.844,"140":1,"160":1.125},
 "12": {"STD":.375,"XS":.5,"XXS":1,"20":.25,"30":.33,"40":.406,"60":.562,"80":.688,"100":.844,"120":1,"140":1.125,"160":1.312},
 "14": {"STD":.375,"XS":.5,"20":.312,"30":.375,"40":.438,"60":.594,"80":.75,"100":.938,"120":1.094,"140":1.25,"160":1.406},
 "16": {"STD":.375,"XS":.5,"20":.312,"30":.375,"40":.5,"60":.656,"80":.844,"100":1.031,"120":1.219,"140":1.438,"160":1.594},
 "18": {"STD":.375,"XS":.5,"20":.312,"30":.438,"40":.562,"60":.75,"80":.938,"100":1.156,"120":1.375,"140":1.562,"160":1.781},
 "20": {"STD":.375,"XS":.5,"20":.375,"30":.5,"40":.594,"60":.812,"80":1.031,"100":1.281,"120":1.5,"140":1.75,"160":1.969},
 "24": {"STD":.375,"XS":.5,"20":.375,"30":.562,"40":.688,"60":.969,"80":1.219,"100":1.531,"120":1.812,"140":2.062,"160":2.344},
}
# Sandvik, Tube and pipe catalogue 2009, stainless pipe size chart to ASTM B36.10 / B36.19, wall mm.
# Column "10S" for NPS 1/2 to 12 and column "10" for NPS 14 and up.
SV_SCH10_MM = {"1/2":2.11,"3/4":2.11,"1":2.77,"1 1/4":2.77,"1 1/2":2.77,"2":2.77,"2 1/2":3.05,"3":3.05,"3 1/2":3.05,"4":3.05,
               "5":3.40,"6":3.40,"8":3.76,"10":4.19,"12":4.57,"14":6.35,"16":6.35,"18":6.35,"20":6.35,"24":6.35}

# Sandvik chart (same page), used only as a tie-break when Weldbend and Hackney Ladish differ. Wall mm.
SV_TIE_MM = {("2", "XXS"): 11.07}

SCHEDULES = ["10","20","30","40","STD","60","XS","80","100","120","140","160","XXS"]

def mm(x): return round(x * 25.4, 2)

def main():
    os.makedirs(OUT, exist_ok=True)
    def fit(fid, name, dim, rows, measured):
        return {"id": fid, "name": name, "dimension": dim, "measuredTo": measured,
                "rows": [{"nps": n, "in": v, "mm": mm(v)} for n, v in rows]}
    b169 = {"standard": "ASME B16.9", "note": "Centre-to-end dimensions. Inch values are the published fractions; mm = in x 25.4.",
      "sources": [
        {"publisher": "Weldbend Corporation", "catalogue": "Carbon Steel Weld Fitting and Weld Flange Catalog, 63rd edition", "year": 2010, "pages": "26, 29 to 37, 38, 49, 62 to 68", "url": "https://www.weldbend.com/catalog.pdf"},
        {"publisher": "Hackney Ladish, Inc.", "catalogue": "Dimension Data: Straight Fitting Dimensions and Reducing Fittings", "year": 2006, "pages": "1 and 5", "url": "https://www.hackneyladish.com/documents/AllData.pdf"}],
      "fittings": [
        fit("BW_90LR", "90 elbow, long radius", "A", merge("B16.9 90 LR", BW_SIZES, WB_90LR, HL_90LR), "centre"),
        fit("BW_90SR", "90 elbow, short radius", "A", merge("B16.9 90 SR", BW_SIZES, WB_90SR, HL_90SR), "centre"),
        fit("BW_45LR", "45 elbow, long radius", "B", merge("B16.9 45 LR", BW_SIZES, WB_45LR, HL_45LR), "centre"),
        fit("BW_TEE", "Straight tee", "C", merge("B16.9 tee", BW_SIZES, WB_TEE, HL_TEE), "centre"),
        fit("BW_REDUCER", "Concentric reducer", "H", merge("B16.9 reducer", BW_SIZES, WB_RED, HL_RED), "far end"),
      ]}
    b1611 = {"standard": "ASME B16.11", "note": "Class 3000 socket weld. Centre to bottom of socket; coupling value is the gap between socket bottoms. mm = in x 25.4.",
      "sources": [
        {"publisher": "Bonney Forge", "catalogue": "Forged Steel Fittings and Unions", "year": None, "pages": "6, 7, 8, 14", "url": "https://cad.bonneyforge.com/Asset/Forged-Steel-Fittings-and-Unions.pdf"},
        {"publisher": "Anvil International", "catalogue": "Pipe Fittings and Steel Nipples catalogue, Forged Steel Fittings, Class 3000 Socket Weld, figures 2150 to 2154", "year": None, "pages": "100 to 102", "url": "https://www.apsupplies.com/wp-content/uploads/anvil-fittings.pdf"}],
      "fittings": [
        fit("SW_90", "90 elbow", "A", merge("B16.11 90", SW_SIZES, BF_90, AN_90, denom=32), "centre"),
        fit("SW_45", "45 elbow", "A", merge("B16.11 45", SW_SIZES, BF_45, AN_45, denom=32), "centre"),
        fit("SW_TEE", "Tee", "A", merge("B16.11 tee", SW_SIZES, BF_TEE, AN_TEE, denom=32), "centre"),
        fit("SW_COUPLING", "Coupling", "E", merge("B16.11 coupling", SW_SIZES, BF_CPL, AN_CPL, denom=32), "coupling centre (half of E each side)"),
      ]}
    # Flanges
    classes = []
    for cls, a, b, c in ((150, WB150, UF150, CF150), (300, WB300, UF300, CF300)):
        rows = []
        for i, nps in enumerate(FL_SIZES):
            ok = True
            n_ok = a["n"][i] == b["n"][i] == c["n"][i]
            if not n_ok: log.append(f"B16.5 {cls} NPS {nps}: bolt count disagreement"); ok = False
            if not agree(f(a["d"][i]), f(b["d"][i])): log.append(f"B16.5 {cls} NPS {nps}: stud size disagreement"); ok = False
            if not agree(f(a["L"][i]), f(b["L"][i])): log.append(f"B16.5 {cls} NPS {nps}: stud length disagreement"); ok = False
            bc = merge(f"B16.5 {cls} bolt circle", [nps], [a["bc"][i]], [b["bc"][i]], [c["bc"][i]], denom=8)
            if not bc: ok = False
            if ok:
                rows.append({"nps": nps, "bolts": a["n"][i], "studIn": a["d"][i], "boltCircleIn": bc[0][1], "boltCircleMm": mm(bc[0][1]),
                             "studLengthIn": snap(f(a["L"][i]), 8), "studLengthMm": mm(snap(f(a["L"][i]), 8))})
        classes.append({"class": cls, "rows": rows})
    b165 = {"standard": "ASME B16.5", "note": "Raised face 0.06 in stud bolt lengths. No torque values.",
      "sources": [
        {"publisher": "Weldbend Corporation", "catalogue": "Carbon Steel Weld Fitting and Weld Flange Catalog, 63rd edition", "year": 2010, "pages": "86, 88", "url": "https://www.weldbend.com/catalog.pdf"},
        {"publisher": "USA Fastener (MW Components)", "catalogue": "Bolt Reference Charts for ASME B16.5 Flanges", "year": None, "pages": "1", "url": "https://info.mwcomponents.com/hubfs/PDFs/MW-COMPONENTS/MWC-Product-Resources-and-Technical-Data-PDFs/USA_Fastener_ASME_B16.5_Flange_Charts.pdf"},
        {"publisher": "Coastal Flange", "catalogue": "Online catalogue, ANSI B16.5 Class 150 and Class 300 welding neck flanges", "year": None, "pages": "bolt circle and hole count used as the tie-break", "url": "https://catalog.coastalflange.com/category/ansi-b16-5-class-150-flanges"}],
      "classes": classes}
    # Pipe
    sizes = []
    for i, nps in enumerate(PIPE_SIZES):
        assert abs(PIPE_OD[i] - HL_OD[i]) < 0.002, nps
        walls = {}
        for s in SCHEDULES:
            w = WB_PIPE[nps].get(s)
            if s == "10":
                sv = SV_SCH10_MM.get(nps)
                if w is not None and sv is not None and abs(w * 25.4 - sv) <= 0.03:
                    walls[s] = w
                elif w is not None or sv is not None:
                    log.append(f"B36.10M NPS {nps} Sch 10: dropped")
                continue
            h = HL_PIPE[nps].get(s)
            if w is not None and h is not None and abs(w - h) <= 0.0015:
                walls[s] = w
            elif w is not None and h is not None and (nps, s) in SV_TIE_MM and (abs(w * 25.4 - SV_TIE_MM[(nps, s)]) <= 0.03 or abs(h * 25.4 - SV_TIE_MM[(nps, s)]) <= 0.03):
                v = w if abs(w * 25.4 - SV_TIE_MM[(nps, s)]) <= 0.03 else h
                walls[s] = v
                log.append(f"B36.10M NPS {nps} Sch {s}: sources disagree (Weldbend {w}, Hackney {h}), Sandvik {SV_TIE_MM[(nps, s)]} mm confirms {v}")
            elif w is not None or h is not None:
                log.append(f"B36.10M NPS {nps} Sch {s}: dropped (Weldbend {w}, Hackney {h})")
        sizes.append({"nps": nps, "odIn": PIPE_OD[i], "odMm": mm(PIPE_OD[i]), "wallsIn": walls})
    pipe = {"standard": "ASME B36.10M and B36.19M", "note": "Wall in inches; mm = in x 25.4. Plain-end weight kg/m = 0.0246615 (D - t) t with D and t in mm. Sch 10 at NPS 12 and below has the same wall as 10S.",
      "sources": [
        {"publisher": "Weldbend Corporation", "catalogue": "Carbon Steel Weld Fitting and Weld Flange Catalog, 63rd edition, Dimensions of Steel Pipe", "year": 2010, "pages": "138 to 143", "url": "https://www.weldbend.com/catalog.pdf"},
        {"publisher": "Hackney Ladish, Inc.", "catalogue": "Dimension Data: Common Diameters and Wall Thicknesses", "year": 2006, "pages": "4", "url": "https://www.hackneyladish.com/documents/AllData.pdf"},
        {"publisher": "Sandvik", "catalogue": "Tube and pipe catalogue, stainless steel pipe size and weight chart (ASTM B36.10 / B36.19)", "year": 2009, "pages": "28 (Sch 10 and 10S column)", "url": "https://www.salaty.com.my/wp-content/uploads/pdf/SANDVIK/PDS/Sandvik%20Tube-pipe%20Cat2009C.pdf"}],
      "schedules": SCHEDULES, "sizes": sizes}
    for name, obj in (("asme_b16_9.json", b169), ("asme_b16_11.json", b1611), ("asme_b16_5_bolts.json", b165), ("asme_b36_10_19.json", pipe)):
        with open(os.path.join(OUT, name), "w") as fh:
            json.dump(obj, fh, indent=1, ensure_ascii=True)
    print("\n".join(log))
    print("counts:", {x["id"]: len(x["rows"]) for x in b169["fittings"] + b1611["fittings"]},
          {c["class"]: len(c["rows"]) for c in classes}, sum(len(s["wallsIn"]) for s in sizes), "pipe walls")

main()
