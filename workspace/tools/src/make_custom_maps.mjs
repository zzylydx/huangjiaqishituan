#!/usr/bin/env node
/**
 * 扩展包生成器：把原版 20 张地图（m0-m7 战役 + s0-s11 竞技场）水平镜像重排，
 * 生成 workspace/res_custom/240_320.pak（扩展版资源包）。
 *
 * 地图格式（自 f.class/b 构造器逆向，全部小端 DataInputStream 序）：
 *   int width, int height,
 *   byte terrain[width*height]  （x 列优先；值=瓦片 id 0..36；27=出生/触发点）
 *   int N; skip N*4             （平行 int 数组，原样保留）
 *   int M; M × { byte type; short px; short py }  （type%12=兵种, type/12=阵营, 9=主帅；px/py 为像素坐标, /24=格）
 *
 * 镜像：地形列反转 + 单位 px 镜像(px' = width*24 - px - 24)，出生点(27)与单位
 * 全部同步变换 → 战术流程反转但完全自洽可玩。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const WS = path.dirname(path.dirname(path.dirname(fileURLToPath(import.meta.url)))) // workspace/
const SRC = path.join(WS, 'res', '240_320.pak')
const OUTDIR = path.join(WS, 'res_custom')
const OUT = path.join(OUTDIR, '240_320.pak')

// ---------- pak 目录顺序读（与 j.c() 一致：readFully 顺序排列，目录 int 偏移字段无效） ----------
function parsePak(buf) {
  let pos = 0
  const headerSize = buf.readInt16BE(pos); pos += 2
  const count = buf.readInt16BE(pos); pos += 2
  const names = [], lens = []
  for (let i = 0; i < count; i++) {
    const ul = buf.readInt16BE(pos); pos += 2
    names.push(buf.toString('latin1', pos, pos + ul)); pos += ul
    pos += 4 // 目录 int 偏移（读取时未使用）
    lens.push(buf.readInt16BE(pos)); pos += 2
  }
  const entries = {}
  for (let i = 0; i < count; i++) {
    entries[names[i]] = buf.subarray(pos, pos + lens[i])
    pos += lens[i]
  }
  if (pos !== buf.length) throw new Error(`pak 解析错位: pos=${pos} size=${buf.length}`)
  return { names, entries }
}

// ---------- 地图编解码 ----------
function decodeMap(b) {
  let p = 0
  const w = b.readInt32BE(p); p += 4
  const h = b.readInt32BE(p); p += 4
  const terrain = b.subarray(p, p + w * h); p += w * h
  const nSkip = b.readInt32BE(p); p += 4
  const skip = b.subarray(p, p + nSkip * 4); p += nSkip * 4
  const units = []
  const m = b.readInt32BE(p); p += 4
  for (let i = 0; i < m; i++) {
    units.push({ type: b.readUInt8(p), px: b.readInt16BE(p + 1), py: b.readInt16BE(p + 3) })
    p += 5
  }
  if (p !== b.length) throw new Error(`地图尾部多余 ${b.length - p} 字节`)
  return { w, h, terrain, nSkip, skip, units }
}

function encodeMap(m) {
  const out = []
  const h4 = (v) => { const b = Buffer.alloc(4); b.writeInt32BE(v); out.push(b) }
  const h2 = (v) => { const b = Buffer.alloc(2); b.writeInt16BE(v); out.push(b) }
  h4(m.w); h4(m.h)
  out.push(m.terrain)
  h4(m.nSkip); out.push(m.skip)
  h4(m.units.length)
  for (const u of m.units) {
    const b = Buffer.alloc(5)
    b.writeUInt8(u.type, 0); b.writeInt16BE(u.px, 1); b.writeInt16BE(u.py, 3)
    out.push(b)
  }
  return Buffer.concat(out)
}

function mirrorMap(m) {
  const t = Buffer.from(m.terrain) // 不改原数据
  for (let x = 0; x < m.w; x++) {
    for (let y = 0; y < m.h; y++) {
      t[x * m.h + y] = m.terrain[(m.w - 1 - x) * m.h + y]
    }
  }
  const units = m.units.map(u => ({ ...u, px: m.w * 24 - u.px - 24 }))
  return { ...m, terrain: t, units }
}

// ---------- 主流程 ----------
const { names, entries } = parsePak(fs.readFileSync(SRC))
const mapNames = names.filter(n => /^(m|s)\d+$/.test(n))
console.log(`原版 pak: ${names.length} 条目，其中地图 ${mapNames.length} 张`)

const overrides = {}
for (const n of mapNames) {
  const mirrored = encodeMap(mirrorMap(decodeMap(entries[n])))
  overrides[n] = mirrored
  const d = decodeMap(mirrored)
  console.log(`  ${n}: ${d.w}x${d.h} 地形${d.w * d.h}B 单位${d.units.length} → 镜像完成`)
}

// 重新打包：目录顺序、格式与原版一致（headerSize/count/name/offset(int,占位)/len + 顺序数据）
const dirRows = names.map(n => {
  const data = overrides[n] || entries[n]
  return { n, data }
})
const dirBufs = []
let headerSize = 4
for (const { n } of dirRows) {
  headerSize += 2 + n.length + 4 + 2
}
const dataBufs = []
for (const { n, data } of dirRows) {
  const b = Buffer.alloc(2 + n.length + 4 + 2)
  b.writeInt16BE(n.length, 0)
  b.write(n, 2, 'latin1')
  b.writeInt32BE(0, 2 + n.length) // 偏移占位（游戏不使用）
  b.writeInt16BE(data.length, 2 + n.length + 4)
  dirBufs.push(b)
  dataBufs.push(data)
}
const pak = Buffer.concat([
  Buffer.from([headerSize >> 8, headerSize & 0xFF]),
  Buffer.from([names.length >> 8, names.length & 0xFF]),
  ...dirBufs, ...dataBufs,
])
fs.mkdirSync(OUTDIR, { recursive: true })
fs.writeFileSync(OUT, pak)
console.log(`扩展包 → ${OUT} (${(pak.length / 1024).toFixed(1)}KB，原版 ${(fs.statSync(SRC).size / 1024).toFixed(1)}KB)`)
