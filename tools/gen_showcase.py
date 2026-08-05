#!/usr/bin/env python3
"""Generate docs/clusters.html — the cluster showcase — from ClusterTheme.kt.

The page renders every DashLayout as an SVG mock using the exact theme
colours and the same gauge geometry the app draws with. Re-run after adding
or editing layouts:

    python3 tools/gen_showcase.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KT = ROOT / "app/src/main/java/com/gt7telemetry/dash/ClusterTheme.kt"
OUT = ROOT / "docs/clusters.html"

FAMILY = {
    "DEFAULT": "default", "CENTRAL": "central", "TWIN": "twin",
    "FIVE_DIAL": "five", "BAR": "bar", "MINIMAL": "minimal",
    "TILES": "tiles", "DIGITAL_RING": "ring", "OFFSET": "offset",
}

# Tach flavour per layout (top-of-scale, redline, both in x1000 rpm).
# Anything not listed reads 8 / 7 — a sensible road-car tach.
TACH = {
    "FERRARI": (10, 9), "LAMBORGHINI": (10, 9), "MCLAREN": (9, 8.2),
    "LFA": (10, 9), "FORMULA1": (12, 11), "PAGANI": (8, 7.5),
    "KOENIGSEGG": (9, 8.2), "CLASSIC": (6, 5.5), "HELLCAT": (7, 6.2),
    "PORSCHE_GT3_RS": (10, 9), "MAZDA_787B": (10, 9),
    "TOYOTA_GR010": (10, 9), "SUZUKI_VGT": (11, 10), "GT_VGT": (10, 9),
    "MASERATI_MC20": (9, 8), "HYUNDAI_N74": (9, 8), "PEUGEOT_9X8": (9, 8.5),
    "GENESIS_X": (10, 9), "MUSTANG_GTD": (8, 7.5), "SUBARU_STI": (8, 6.7),
    "EVO_FINAL": (8, 7), "ALFA_GTAM": (8, 7.3), "ABARTH_695": (7, 6.3),
    "KTM_XBOW": (9, 8), "RADICAL": (10, 9.2), "TVR_TUSCAN": (8, 7.5),
    "BAC_MONO": (10, 9.2), "XIAOMI_SU7": (9, 8), "YANGWANG_U9": (9, 8),
}

KEYS = ["bg", "panel", "line", "ink", "ink2", "mute",
        "acc", "ndl", "red", "good", "face", "dialText", "dialGear"]


def parse_layouts(src: str):
    body = src[src.index("enum class DashLayout"):]
    entries = re.finditer(
        r'^\s{4}([A-Z][A-Z_0-9]*)\("([^"]+)",\s*("(?P<mfr>[^"]+)"|null),\s*'
        r"LayoutFamily\.(\w+),", body, re.M)
    spans = [(m, m.start()) for m in entries]
    out = []
    for i, (m, start) in enumerate(spans):
        end = spans[i + 1][1] if i + 1 < len(spans) else body.index("companion object")
        block = body[start:end]
        hexes = re.findall(r"c\(0x([0-9A-Fa-f]{8})\)", block)
        t = {k: "#" + hexes[j][2:] for j, k in enumerate(KEYS)}
        ring = re.search(r"ring\s*=\s*c\(0x([0-9A-Fa-f]{8})\)", block)
        if ring:
            t["ring"] = "#" + ring.group(1)[2:]
        name = m.group(1)
        maxk, redk = TACH.get(name, (8, 7))
        e = {
            "id": name, "n": m.group(2), "auto": m.group("mfr"),
            "f": FAMILY[m.group(5)], "t": t, "maxK": maxk, "redK": redk,
        }
        if "italic = true" in block:
            e["it"] = 1
        if "heroSpeed = true" in block:
            e["heroSpd"] = 1
            spd = re.search(r"spdMax\s*=\s*(\d+)", block)
            e["spdMax"] = int(spd.group(1)) if spd else 240
            e["spd"] = round(e["spdMax"] * 0.55)
        out.append(e)
    return out


def main():
    layouts = parse_layouts(KT.read_text())
    marque = [e for e in layouts if e["id"] != "DEFAULT"]
    autos = [e for e in layouts if e["auto"]]
    html = TEMPLATE
    html = html.replace("__DATA__", json.dumps(layouts, ensure_ascii=False))
    html = html.replace("__COUNT__", str(len(marque)))
    html = html.replace("__AUTOS__", str(len(autos)))
    OUT.parent.mkdir(exist_ok=True)
    OUT.write_text(html)
    print(f"{OUT.relative_to(ROOT)}: {len(layouts)} layouts "
          f"({len(autos)} auto-selectable)")


TEMPLATE = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>GT7 Telemetry — Instrument Clusters</title>
<style>
  :root{
    --bg:#0D1017; --panel:#141926; --line:#232C42; --ink:#EDF1F8; --ink2:#8B96AB;
    --blue:#2D6BFF; --red:#E62E32;
  }
  @media (prefers-color-scheme: light){
    :root{ --bg:#EFF1F5; --panel:#FFFFFF; --line:#D8DDE7; --ink:#171C26; --ink2:#5A6478; }
  }
  *{box-sizing:border-box}
  body{ margin:0; background:var(--bg); color:var(--ink);
    font:15px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif; }
  .wrap{ max-width:1200px; margin:0 auto; padding:28px 20px 60px; }
  .cond{ font-family:"Avenir Next Condensed","Arial Narrow","Roboto Condensed","Liberation Sans Narrow",system-ui,sans-serif; }
  header{ border-bottom:2px solid var(--line); padding-bottom:18px; margin-bottom:26px; }
  .eyebrow{ font-size:12px; letter-spacing:.22em; text-transform:uppercase; color:var(--ink2); margin:0 0 6px; }
  .eyebrow b{ color:var(--red); font-weight:700; }
  h1{ margin:0 0 8px; font-size:clamp(26px,4.5vw,40px); line-height:1.08; letter-spacing:-.01em;
      text-wrap:balance; font-weight:800; text-transform:uppercase; }
  h1 .b{ color:var(--blue); } h1 .r{ color:var(--red); }
  .sub{ max-width:68ch; color:var(--ink2); margin:0; }
  h2{ font-size:13px; letter-spacing:.2em; text-transform:uppercase; color:var(--ink2);
      margin:40px 0 14px; font-weight:700; }
  .grid{ display:grid; grid-template-columns:repeat(auto-fill,minmax(330px,1fr)); gap:16px; }
  .card{ background:var(--panel); border:1px solid var(--line); border-radius:10px; overflow:hidden; }
  .card svg{ display:block; width:100%; height:auto; background:#000; }
  .meta{ padding:10px 13px 12px; display:flex; align-items:baseline; gap:10px; flex-wrap:wrap; }
  .meta .name{ font-weight:800; font-size:15.5px; letter-spacing:.01em; }
  .meta .fam{ margin-left:auto; font-size:11px; letter-spacing:.14em; text-transform:uppercase;
    color:var(--ink2); border:1px solid var(--line); border-radius:999px; padding:2px 9px; white-space:nowrap; }
  .meta .auto{ font-size:12px; color:var(--ink2); }
  .meta .auto b{ color:var(--blue); font-weight:600; }
  .portrait-row{ display:grid; grid-template-columns:repeat(auto-fill,minmax(190px,1fr)); gap:16px; align-items:start; }
  .note{ max-width:66ch; color:var(--ink2); }
  .note b{ color:var(--ink); }
  footer{ margin-top:44px; border-top:1px solid var(--line); padding-top:14px; color:var(--ink2); font-size:13px; }
</style>
</head>
<body>
<div class="wrap">
<header>
  <p class="eyebrow cond">GT7 Telemetry · <b>__COUNT__ marque clusters</b> · 9 families</p>
  <h1 class="cond">Instrument clusters<br><span class="b">every marque,</span> <span class="r">top of the line</span></h1>
  <p class="sub">Every dashboard layout the app ships, drawn here with the exact theme values and
  gauge geometry from <code>ClusterTheme.kt</code>. Auto mode matches __AUTOS__ of them to the car
  you're driving; every catalog manufacturer resolves to one of these, directly or via an alias.
  This page is generated — <code>python3 tools/gen_showcase.py</code> after adding a layout.</p>
</header>

<h2 class="cond">All layouts</h2>
<div class="grid" id="grid"></div>

<h2 class="cond">Portrait — data first, hero kept</h2>
<p class="note">Rotate the phone and every family collapses to the same promise: <b>the marque's
hero instrument survives</b> — the tach for a GT3&nbsp;RS, the segmented ring for an MC20, the giant
Smiths speedometer for the Mini — and everything the wide layout showed in dials is <b>re-issued
as digital cards below it</b>: shift lights, rev band, lap / best / delta, tyre pods, fuel-per-lap,
water &amp; oil, and the TCS / handbrake / limiter flags. Nothing is dropped.</p>
<div class="portrait-row" id="pgrid"></div>

<footer>
  Generated from ClusterTheme.kt · GT7 Telemetry · not affiliated with Sony / Polyphony Digital
</footer>
</div>

<script>
const L = __DATA__;
const S = {spd:172, gear:"4", lap:"1:43.2", best:"1:41.8", delta:"+0.4",
           tyres:[74,78,81,83], fuel:64, boost:"1.4"};
const MONO='ui-monospace,SF Mono,Menlo,Consolas,monospace';
const esc = s=>String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;");

function txt(x,y,s,fill,size,o={}){
  return `<text x="${x}" y="${y}" fill="${fill}" font-size="${size}"
    font-family="${o.sans?'system-ui,sans-serif':MONO}" font-weight="${o.w||700}"
    ${o.a?`text-anchor="${o.a}"`:""} ${o.ls?`letter-spacing="${o.ls}"`:""}>${esc(s)}</text>`;
}
const lab=(x,y,s,t,o={})=>txt(x,y,String(s).toUpperCase(),t.ink2,6.5,{...o,w:600,ls:.8,sans:1});
function pol(cx,cy,r,deg){const a=(deg-90)*Math.PI/180;return [cx+r*Math.cos(a),cy+r*Math.sin(a)];}

function dial(cx,cy,r,t,frac,redFrac,o={}){
  let s='';
  const A0=225, SW=270;
  if(o.bezel&&t.ring) s+=`<circle cx="${cx}" cy="${cy}" r="${r+2.5}" fill="none" stroke="${t.ring}" stroke-width="2"/>`;
  s+=`<circle cx="${cx}" cy="${cy}" r="${r}" fill="${t.face}"/>`;
  const N=o.ticks||24;
  for(let i=0;i<=N;i++){
    const f=i/N, deg=A0+SW*f-360, major=i%(N/(o.majors||8))===0;
    const col = redFrac!=null&&f>=redFrac ? t.red : (major?t.ink:t.ink2);
    const [x1,y1]=pol(cx,cy,r-2.5,deg), [x2,y2]=pol(cx,cy,major?r-8:r-5.5,deg);
    s+=`<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${col}" stroke-width="${major?1.6:.8}"/>`;
  }
  if(redFrac!=null){
    const a1=A0+SW*redFrac, a2=A0+SW;
    const [sx,sy]=pol(cx,cy,r-11,a1-360),[ex,ey]=pol(cx,cy,r-11,a2-360);
    const large=(a2-a1)>180?1:0;
    s+=`<path d="M ${sx} ${sy} A ${r-11} ${r-11} 0 ${large} 1 ${ex} ${ey}" fill="none" stroke="${t.red}" stroke-width="2" opacity=".85"/>`;
  }
  const nd=A0+SW*frac-360, [nx,ny]=pol(cx,cy,r-10,nd), [bx,by]=pol(cx,cy,-r*0.14,nd);
  s+=`<line x1="${bx}" y1="${by}" x2="${nx}" y2="${ny}" stroke="${t.ndl}" stroke-width="2.4" stroke-linecap="round"/>
      <circle cx="${cx}" cy="${cy}" r="3" fill="${t.ndl}"/>`;
  return s;
}
function ring(cx,cy,r,t,frac){
  let s=`<circle cx="${cx}" cy="${cy}" r="${r*0.72}" fill="${t.face}"/>`;
  const N=36, A0=225;
  for(let i=0;i<N;i++){
    const f=i/(N-1), deg=A0+270*f-360, on=f<=frac;
    const col=on?(f>.86?t.red:t.acc):t.line;
    const [x1,y1]=pol(cx,cy,r-7,deg),[x2,y2]=pol(cx,cy,r,deg);
    s+=`<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${col}" stroke-width="3" stroke-linecap="round"/>`;
  }
  return s;
}
function shiftLights(x,y,w,t,frac,n=14){
  let s='';const seg=w/n;
  for(let i=0;i<n;i++){const on=i/n<frac;
    const col=on?(i/n>.8?t.red:(i/n>.55?"#FFBF00":t.good)):t.line;
    s+=`<rect x="${x+i*seg}" y="${y}" width="${seg-2}" height="4" rx="1.5" fill="${col}"/>`;}
  return s;
}
function revBar(x,y,w,t,frac,redFrac){
  return `<rect x="${x}" y="${y}" width="${w}" height="5" rx="2" fill="${t.line}"/>
   <rect x="${x}" y="${y}" width="${w*frac}" height="5" rx="2" fill="${frac>redFrac?t.red:t.acc}"/>`;
}
function tile(x,y,w,h,t,label,val,col){
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="3.5" fill="${t.panel}" stroke="${t.line}" stroke-width=".6"/>`
   + lab(x+5,y+9,label,t) + txt(x+5,y+h-5,val,col||t.ink,10);
}
function tyres(x,y,t){
  let s='';S.tyres.forEach((v,i)=>{const c=v>80?"#FFBF00":t.good;
    s+=`<rect x="${x+(i%2)*26}" y="${y+((i/2)|0)*15}" width="23" height="12" rx="2.5" fill="${t.face}" stroke="${c}" stroke-width="1"/>`
      + txt(x+(i%2)*26+11.5,y+((i/2)|0)*15+9,v+"°",c,7,{a:"middle"});});
  return s;
}

function renderLand(e){
  const t=e.t, frac=e.heroSpd?0:(0.76*(e.redK/e.maxK)), rpm=Math.round(e.redK*760);
  const red=e.redK/e.maxK;
  let g='';
  const W=350,H=165;
  if(e.f==="default"){
    g+=txt(14,58,S.spd,t.acc,42)+lab(14,70,"km/h",t);
    g+=txt(W-14,58,S.gear,t.ink,38,{a:"end"})+lab(W-14,70,"gear",t,{a:"end"});
    g+=shiftLights(10,82,W-20,t,0.72)+revBar(10,94,W-20,t,0.76,red);
    g+=tile(10,108,110,26,t,"Lap 12",S.lap)+tile(126,108,110,26,t,"Best",S.best,t.good)
      +tile(242,108,98,26,t,"Δ",S.delta,t.red);
    g+=lab(10,148,"Tyres °C",t)+tyres(60,138,t);
    g+=tile(200,138,140,22,t,"Fuel · Boost",S.fuel+"% · "+S.boost);
  } else if(e.f==="central"){
    const cx=W/2,cy=86,r=64;
    g+=dial(cx,cy,r,t,e.heroSpd?(e.spd/e.spdMax):frac,e.heroSpd?null:red,{bezel:1,majors:e.heroSpd?4:8});
    g+=txt(cx,cy+4,S.gear,t.ink,26,{a:"middle"})+txt(cx,cy+20,e.heroSpd?"3400":S.spd,t.ink2,9,{a:"middle"});
    g+=lab(cx,cy+31,e.heroSpd?"rpm":"km/h",t,{a:"middle"});
    g+=tile(10,26,72,26,t,"Lap 12",S.lap)+tile(10,58,72,26,t,"Best",S.best,t.good)+tile(10,90,72,26,t,"Δ",S.delta,t.red);
    g+=tile(268,26,72,26,t,"Fuel",S.fuel+"%")+tile(268,58,72,26,t,"Boost",S.boost);
    g+=lab(268,98,"Tyres °C",t)+tyres(272,104,t);
    g+=shiftLights(10,132,W-20,t,0.72)+revBar(10,146,W-20,t,0.76,0.86);
  } else if(e.f==="twin"){
    const r=52;
    g+=dial(105,78,r,t,frac,red,{bezel:1});
    g+=txt(105,82,(rpm/1000).toFixed(1)+"k",t.ink2,8,{a:"middle"}); g+=lab(105,94,"rpm ×1000",t,{a:"middle"});
    g+=dial(245,78,r,t,S.spd/260,null,{bezel:1,majors:6});
    g+=txt(245,74,S.spd,t.ink,12,{a:"middle"})+lab(245,86,"km/h",t,{a:"middle"});
    g+=txt(245,98,"GEAR "+S.gear,t.acc,8,{a:"middle"});
    g+=tile(10,138,78,22,t,"Lap 12",S.lap)+tile(94,138,78,22,t,"Best",S.best,t.good)
      +tile(178,138,78,22,t,"Δ",S.delta,t.red)+tile(262,138,78,22,t,"Tyres","74 78 81 83");
  } else if(e.f==="five"){
    g+=dial(60,80,40,t,S.spd/240,null,{majors:6}); g+=txt(60,84,S.spd,t.ink,9,{a:"middle"});g+=lab(60,95,"km/h",t,{a:"middle"});
    g+=dial(160,78,58,t,frac,red); g+=txt(160,82,S.gear,t.ink,22,{a:"middle"})+lab(160,96,rpm+" rpm",t,{a:"middle"});
    g+=dial(255,80,40,t,0.35,null,{majors:4}); g+=txt(255,84,S.boost,t.ink,9,{a:"middle"});g+=lab(255,95,"boost",t,{a:"middle"});
    g+=tile(300,32,42,24,t,"Lap",S.lap)+tile(300,62,42,24,t,"Best",S.best,t.good);
    g+=lab(300,102,"Tyres",t)+tyres(300,108,t);
  } else if(e.f==="bar"){
    g+=shiftLights(10,14,W-20,t,0.72);
    g+=txt(14,88,S.spd,t.ink,40)+lab(14,102,"km/h",t);
    g+=txt(W/2,100,S.gear,t.ink,64,{a:"middle"})+lab(W/2,114,"gear",t,{a:"middle"});
    g+=txt(W-14,84,rpm,t.ink,22,{a:"end"})+lab(W-14,98,"rpm / "+(e.maxK*1000),t,{a:"end"});
    g+=`<rect x="10" y="132" width="46" height="16" rx="3.5" fill="${t.red}"/>`+txt(33,143.5,S.delta,"#FFF",9,{a:"middle"});
    g+=txt(64,143.5,"L12 "+S.lap+" · best "+S.best,t.ink2,9);
    g+=txt(W-12,143.5,"FL74 FR78 RL81 RR83",t.ink2,8.5,{a:"end"});
  } else if(e.f==="minimal"){
    g+=txt(W/2,86,S.spd,t.ink,58,{a:"middle"});
    g+=txt(W/2,106,"KM/H  ·  GEAR ",t.ink2,8.5,{a:"middle"});
    g+=txt(W/2+44,106,S.gear,t.acc,9);
    g+=revBar(10,120,W-20,t,0.76,0.86);
    g+=tile(10,134,78,24,t,"Lap",S.lap)+tile(94,134,78,24,t,"Best",S.best,t.good)
      +tile(178,134,78,24,t,"Δ",S.delta,t.red)+tile(262,134,78,24,t,"Tyres","74 78 81 83");
  } else if(e.f==="tiles"){
    g+=dial(72,80,55,t,frac,red);
    g+=txt(72,84,S.gear,t.ink,20,{a:"middle"})+lab(72,98,rpm+"",t,{a:"middle"});
    const cw=64,ch=30,gx=150,gy=30;
    [["Speed",S.spd],["Boost",S.boost],["Fuel",S.fuel+"%"],["Lap 12",S.lap],["Best",S.best],["Δ",S.delta]]
      .forEach((c,i)=>{const col=c[0]==="Best"?t.good:(c[0]==="Δ"?t.red:t.ink);
        g+=tile(gx+(i%3)*(cw+6),gy+((i/3)|0)*(ch+6),cw,ch,t,c[0],c[1],col);});
    g+=lab(gx,112,"Tyres °C",t)+tyres(gx+2,118,t);
  } else if(e.f==="ring"){
    g+=ring(95,82,62,t,0.76);
    g+=txt(95,80,S.gear,t.acc,26,{a:"middle"})+txt(95,96,S.spd,t.ink,11,{a:"middle"})
      +lab(95,108,rpm+" / "+(e.maxK*1000),t,{a:"middle"});
    g+=tile(196,30,144,28,t,"Lap 12",S.lap)+tile(196,64,144,28,t,"Best · Δ",S.best+"   "+S.delta,t.good);
    g+=lab(196,108,"Tyres °C",t)+tyres(200,114,t);
  } else { // offset
    g+=dial(92,84,62,t,frac,red,{bezel:1});
    g+=txt(92,88,S.gear,t.ink,22,{a:"middle"})+lab(92,102,rpm+" rpm",t,{a:"middle"});
    g+=txt(190,72,S.spd,t.ink,38)+lab(190,84,"km/h",t);
    g+=tile(190,96,72,26,t,"Lap 12",S.lap)+tile(268,96,72,26,t,"Δ best",S.delta,t.red);
    g+=lab(190,136,"Tyres °C",t)+tyres(194,142,t);
  }
  return `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="${esc(e.n)} cluster preview">
    <rect width="${W}" height="${H}" fill="${t.bg}"/>${e.it?`<g font-style="italic">${g}</g>`:g}</svg>`;
}

function renderPort(e,heroKind){
  const t=e.t,W=170,H=300, frac=0.76*(e.redK/e.maxK), red=e.redK/e.maxK;
  let g='';
  if(heroKind==="ring"){ g+=ring(W/2,74,56,t,0.76);
    g+=txt(W/2,72,S.gear,t.acc,24,{a:"middle"})+txt(W/2,88,S.spd,t.ink,10,{a:"middle"});
  } else if(heroKind==="digits"){
    g+=txt(W/2,66,S.spd,t.ink,44,{a:"middle"})+lab(W/2,80,"km/h",t,{a:"middle"});
    g+=txt(W/2,104,"GEAR "+S.gear,t.acc,11,{a:"middle"});
  } else if(heroKind==="speed"){
    g+=dial(W/2,74,56,t,(e.spd||87)/(e.spdMax||160),null,{bezel:1,majors:4});
    g+=txt(W/2,78,S.gear,t.ink,20,{a:"middle"})+lab(W/2,92,"3400 rpm",t,{a:"middle"});
  } else { g+=dial(W/2,74,56,t,frac,red,{bezel:1});
    g+=txt(W/2,78,S.gear,t.ink,20,{a:"middle"})+txt(W/2,92,S.spd,t.ink,9,{a:"middle"});
  }
  g+=shiftLights(10,140,W-20,t,0.72)+revBar(10,150,W-20,t,0.76,red);
  g+=tile(10,162,72,26,t,"Lap 12",S.lap)+tile(88,162,72,26,t,"Δ",S.delta,t.red);
  g+=tile(10,194,72,34,t,"Best",S.best,t.good);
  g+=`<rect x="88" y="194" width="72" height="34" rx="3.5" fill="${t.panel}" stroke="${t.line}" stroke-width=".6"/>`
    +lab(93,203,"Tyres",t)+tyres(94,208,t);
  g+=tile(10,234,150,24,t,"Fuel / lap · laps left","2.1% · 17");
  g+=tile(10,264,72,24,t,"Water","86°")+tile(88,264,72,24,t,"Oil","102°");
  return `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="portrait preview">
    <rect width="${W}" height="${H}" rx="10" fill="${t.bg}"/>${g}</svg>`;
}

document.getElementById("grid").innerHTML = L.map(e=>`
  <figure class="card" style="margin:0">
    ${renderLand(e)}
    <figcaption class="meta">
      <span class="name cond">${esc(e.n)}</span>
      <span class="auto">${e.auto?`auto for <b>${esc(e.auto)}</b>`:"manual pick"}</span>
      <span class="fam cond">${e.f==="five"?"five-dial":e.f}</span>
    </figcaption>
  </figure>`).join("");

const byId=Object.fromEntries(L.map(e=>[e.id,e]));
const P=[["PORSCHE_GT3_RS","tach","911 GT3 RS — the tach survives"],
         ["MASERATI_MC20","ring","MC20 — the ring survives"],
         ["MINI_COOPER","speed","Mini — the speedo survives"],
         ["MUSTANG_GTD","digits","Mustang GTD — big digits"]]
  .filter(([id])=>byId[id]);
document.getElementById("pgrid").innerHTML = P.map(([id,k,cap])=>`
  <figure class="card" style="margin:0">${renderPort(byId[id],k)}
    <figcaption class="meta"><span class="auto">${esc(cap)}</span></figcaption>
  </figure>`).join("");
</script>
</body>
</html>
"""

if __name__ == "__main__":
    main()
