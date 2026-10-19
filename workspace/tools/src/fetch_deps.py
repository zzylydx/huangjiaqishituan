#!/usr/bin/env python3
"""Fetch TeaVM 0.16.0 artifact closure from Maven Central into workspace/lib."""
import os, sys, urllib.request, xml.etree.ElementTree as ET

REPO = "https://repo1.maven.org/maven2"
VERSION = "0.16.0"
LIB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

seen_poms = set()
artifacts = {}  # (g,a,v) -> packaging

def path_for(g, a, v, ext, classifier=None):
    c = f"-{classifier}" if classifier else ""
    return f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}{c}.{ext}"

def fetch(rel):
    url = f"{REPO}/{rel}"
    last = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return r.read()
        except Exception as e:
            last = e
            import time
            time.sleep(1.5 * (attempt + 1))
    print(f"  !! FAIL {rel}: {last}")
    return None

def parse_pom(data, g, a, v):
    """Return (properties dict, parent coords, deps list)."""
    root = ET.fromstring(data)
    props = {}
    pv = root.find("m:properties", NS)
    if pv is not None:
        for p in pv:
            props[p.tag.split('}')[-1]] = (p.text or "").strip()
    parent = root.find("m:parent", NS)
    pcoords = None
    if parent is not None:
        pg = parent.findtext("m:groupId", "", NS)
        pa = parent.findtext("m:artifactId", "", NS)
        pvv = parent.findtext("m:version", "", NS)
        pcoords = (pg, pa, pvv)
        props["project.version"] = v
        props["project.groupId"] = g
        props["project.artifactId"] = a
    deps = []
    dd = root.find("m:dependencyManagement", NS)
    dm = {}
    if dd is not None:
        for d in dd.findall(".//m:dependency", NS):
            key = (d.findtext("m:groupId", "", NS), d.findtext("m:artifactId", "", NS))
            dm[key] = (d.findtext("m:version", "", NS), d.findtext("m:scope", "compile", NS),
                       d.findtext("m:optional", "false", NS))
    for d in root.findall(".//m:dependencies/m:dependency", NS):
        dg = d.findtext("m:groupId", "", NS)
        da = d.findtext("m:artifactId", "", NS)
        dv = d.findtext("m:version", "", NS)
        scope = d.findtext("m:scope", "compile", NS)
        opt = d.findtext("m:optional", "false", NS)
        if not dv and (dg, da) in dm:
            dv, scope2, opt2 = dm[(dg, da)]
            scope = scope or scope2
        deps.append((dg, da, dv, scope, opt))
    return props, pcoords, deps

def subst(s, props):
    if not s:
        return s
    for k, val in props.items():
        s = s.replace("${" + k + "}", val)
    return s

def resolve(g, a, v, props_in):
    key = (g, a, v)
    if key in artifacts:
        return
    if g.startswith("org.teavm") is False and not g.startswith(("org.", "com.")):
        print(f"  ?? unexpected group {g}")
    print(f"  .. resolving {g}:{a}:{v}")
    pom_rel = path_for(g, a, v, "pom")
    data = fetch(pom_rel)
    if data is None:
        # some artifacts have no pom (shouldn't happen); still try jar
        jar_rel = path_for(g, a, v, "jar")
        jar = fetch(jar_rel)
        if jar:
            artifacts[key] = "jar"
            save(jar, f"{a}-{v}.jar")
        return
    props, pcoords, deps = parse_pom(data, g, a, v)
    allprops = dict(props_in)
    allprops.update(props)
    # Maven default packaging is jar; only <packaging>pom</packaging> means bare POM
    root = ET.fromstring(data)
    packaging = root.findtext("m:packaging", "jar", NS) or "jar"
    artifacts[key] = packaging
    # download jar if packaging is jar (or missing)
    if packaging in ("jar", None) or packaging == "bundle":
        jar_rel = path_for(g, a, v, "jar")
        jar = fetch(jar_rel)
        if jar:
            save(jar, f"{a}-{v}.jar")
        else:
            artifacts[key] = "MISSING-JAR"
    # recurse into parent first for property context
    if pcoords:
        resolve(*pcoords, props_in=allprops)
    for dg, da, dv, scope, opt in deps:
        dv2 = subst(dv, allprops)
        if not dv2:
            print(f"  !! no version for dep {dg}:{da}, skipping")
            continue
        if scope in ("test", "provided") or opt == "true":
            continue
        if packaging == "pom":
            # parent POMs: follow compile/runtime deps too (build deps skip)
            if scope in ("compile", "runtime", "import"):
                resolve(subst(dg, allprops), da, dv2, props_in=allprops)
        else:
            if scope in ("compile", "runtime"):
                resolve(subst(dg, allprops), da, dv2, props_in=allprops)

def save(data, name):
    out = os.path.join(LIB, name)
    with open(out, "wb") as f:
        f.write(data)
    print(f"     saved {name} ({len(data)//1024} KB)")

if __name__ == "__main__":
    os.makedirs(LIB, exist_ok=True)
    print(f"Fetching TeaVM {VERSION} closure...")
    resolve("org.teavm", "teavm-tooling", VERSION, {})
    ok = [k for k, v in artifacts.items() if v in ("jar", "bundle")]
    print(f"\nDone. {len(ok)} jars in lib/:")
    for f in sorted(os.listdir(LIB)):
        print(" ", f)
