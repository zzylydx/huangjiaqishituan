// 高清重制构建：运行期捕获的素材（buildtmp/hd_dump.json）→ xBRZ 放大 → web/hd.js
// hd.js: window.__hd = { "<len>_<fnv1a32>": "data:image/png;base64,..." }
// 捕获：?dump=1 下 Image.createImage 把游戏解码后的真实图片按指纹倾倒；
// 本脚本只处理 PNG（GIF/BMP 跳过，运行期走原图）。
// 用法: node tools/src/make_hd.mjs  (cwd = workspace)
import fs from 'fs'
import path from 'path'
import zlib from 'zlib'
import { fileURLToPath } from 'url'
import { scaleImage } from './xbrz/xBRZ.js'

const WS = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const DUMP = path.join(WS, 'buildtmp', 'hd_dump.json')
const OUT = path.join(WS, 'web', 'hd.js')
const SCALE_SMALL = 4                     // 小图（sprite/图标）4x
const SCALE_BIG = 3                       // 大图（整屏背景）3x，省内存
const BIG_AREA = 30000                    // ≥ 此像素数视为大图

// ---------- pak 解析（格式逆向自 j.class：<H headerSize, H count, {UTF name, i offset+headerSize, h len}, 顺序 readFully>） ----------
function parsePak(buf) {
  let pos = 0
  const headerSize = buf.readInt16BE(pos); pos += 2
  const count = buf.readInt16BE(pos); pos += 2
  const names = [], offs = [], lens = []
  for (let i = 0; i < count; i++) {
    const ulen = buf.readUInt16BE(pos); pos += 2
    const name = buf.toString('utf8', pos, pos + ulen); pos += ulen
    const off = buf.readInt32BE(pos) + headerSize; pos += 4
    const len = buf.readInt16BE(pos); pos += 2
    names.push(name); offs.push(off); lens.push(len)
  }
  const entries = []
  for (let i = 0; i < count; i++) {
    if (offs[i] < 0 || offs[i] + lens[i] > buf.length) throw new Error(`entry ${names[i]} out of range: off=${offs[i]} len=${lens[i]}`)
    entries.push({ name: names[i], data: buf.subarray(offs[i], offs[i] + lens[i]) })
  }
  return entries
}

// ---------- 最小 PNG 解码（非交错，colorType 0/2/3/4/6，bitDepth 1/2/4/8） ----------
function decodePNG(buf) {
  const SIG = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])
  if (!buf.subarray(0, 8).equals(SIG)) return null
  let pos = 8, width = 0, height = 0, bitDepth = 0, colorType = 0, interlace = 1
  const idat = []
  let plte = null, trns = null
  while (pos + 8 <= buf.length) {
    const len = buf.readUInt32BE(pos)
    const type = buf.toString('ascii', pos + 4, pos + 8)
    const d = buf.subarray(pos + 8, pos + 8 + len)
    if (type === 'IHDR') {
      width = d.readUInt32BE(0); height = d.readUInt32BE(4)
      bitDepth = d[8]; colorType = d[9]; interlace = d[12]
    } else if (type === 'PLTE') plte = d
    else if (type === 'tRNS') trns = d
    else if (type === 'IDAT') idat.push(d)
    else if (type === 'IEND') break
    pos += 12 + len
  }
  if (interlace !== 0) throw new Error('interlaced PNG unsupported: ' + width + 'x' + height)
  const chans = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType]
  if (!chans) throw new Error('bad colorType ' + colorType)
  const stride = Math.ceil(width * chans * bitDepth / 8)
  const raw = zlib.inflateSync(Buffer.concat(idat))
  const img = Buffer.alloc(stride * height)
  const bpp = Math.max(1, Math.floor(chans * bitDepth / 8))
  let p = 0
  for (let y = 0; y < height; y++) {
    const ft = raw[p++]
    const row = img.subarray(y * stride, (y + 1) * stride)
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? row[x - bpp] : 0
      const b = y > 0 ? img[(y - 1) * stride + x] : 0
      const c = (x >= bpp && y > 0) ? img[(y - 1) * stride + x - bpp] : 0
      let v = raw[p + x]
      if (ft === 1) v += a
      else if (ft === 2) v += b
      else if (ft === 3) v += (a + b) >> 1
      else if (ft === 4) {
        const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c)
        v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c)
      }
      row[x] = v & 0xFF
    }
    p += stride
  }
  // → RGBA
  const rgba = Buffer.alloc(width * height * 4)
  const getIdx = (x, y) => { // 供 palette 用：按 bitDepth 取索引
    const bitPos = y * stride * 8 + x * chans * bitDepth
    const byte = img[bitPos >> 3]
    const shift = 8 - bitDepth - (bitPos & 7)
    return (byte >> shift) & ((1 << bitDepth) - 1)
  }
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const o = (y * width + x) * 4
      let r = 0, g = 0, b = 0, a = 255
      if (colorType === 3) {
        const idx = getIdx(x, y)
        r = plte[idx * 3]; g = plte[idx * 3 + 1]; b = plte[idx * 3 + 2]
        if (trns && idx < trns.length) a = trns[idx]
      } else if (colorType === 0) {
        r = g = b = img[y * stride + x]
      } else if (colorType === 2) {
        const s = y * stride + x * 3
        r = img[s]; g = img[s + 1]; b = img[s + 2]
      } else if (colorType === 4) {
        const s = y * stride + x * 2
        r = g = b = img[s]; a = img[s + 1]
      } else if (colorType === 6) {
        const s = y * stride + x * 4
        r = img[s]; g = img[s + 1]; b = img[s + 2]; a = img[s + 3]
      }
      rgba[o] = r; rgba[o + 1] = g; rgba[o + 2] = b; rgba[o + 3] = a
    }
  }
  return { width, height, rgba }
}

// ---------- PNG 编码（RGBA8, filter 0） ----------
const CRC_TABLE = (() => {
  const t = new Int32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1)
    t[n] = c
  }
  return t
})()
function crc32(buf) {
  let c = -1
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8)
  return (c ^ -1) >>> 0
}
function chunk(type, data) {
  const out = Buffer.alloc(12 + data.length)
  out.writeUInt32BE(data.length, 0)
  out.write(type, 4, 'ascii')
  data.copy(out, 8)
  out.writeUInt32BE(crc32(out.subarray(4, 8 + data.length)), 8 + data.length)
  return out
}
function encodePNG(rgba, width, height) {
  const stride = width * 4
  const raw = Buffer.alloc((stride + 1) * height)
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride)
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

// ---------- 指纹（必须与 Image.java hdKey 一致：len + "_" + fnv1a32 无符号十进制） ----------
function fnv1a32(buf) {
  let h = 2166136261
  for (let i = 0; i < buf.length; i++) {
    h ^= buf[i]
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

// ---------- 主流程 ----------
const dump = JSON.parse(fs.readFileSync(DUMP, 'utf-8'))
console.log(`dump: ${Object.keys(dump).length} images`)
const PNGSIG = Buffer.from([137, 80, 78, 71])

const out = []
let totOrig = 0, totHd = 0, pngCount = 0, skip = 0
for (const [key, dataUrl] of Object.entries(dump)) {
  const b64 = dataUrl.slice(dataUrl.indexOf(',') + 1)
  const buf = Buffer.from(b64, 'base64')
  if (!buf.subarray(0, 4).equals(PNGSIG)) {
    skip++
    continue
  }
  let dec
  try {
    dec = decodePNG(buf)
  } catch (err) {
    console.log(`  [warn] ${key}: ${err.message}`)
    skip++
    continue
  }
  if (!dec) { skip++; continue }
  pngCount++
  const scale = dec.width * dec.height >= BIG_AREA ? SCALE_BIG : SCALE_SMALL
  const src = new Int32Array(dec.rgba.buffer, 0, dec.width * dec.height)
  const trg = new Int32Array(dec.width * scale * dec.height * scale)
  scaleImage(scale, src, trg, dec.width, dec.height, 0, dec.height)
  const trgBuf = Buffer.from(trg.buffer)
  const png = encodePNG(trgBuf, dec.width * scale, dec.height * scale)
  out.push(`  "${key}":"data:image/png;base64,${png.toString('base64')}"`)
  totOrig += dec.width * dec.height
  totHd += dec.width * scale * dec.height * scale
}
const js = '/* xBRZ 高清重制素材（构建期生成，勿手改）make_hd.mjs */\nwindow.__hd = {\n' + out.join(',\n') + '\n};\n'
fs.writeFileSync(OUT, js)
console.log(`png=${pngCount} hd=${out.length} skipped=${skip}`)
console.log(`pixels: orig=${(totOrig / 1e6).toFixed(2)}M -> hd=${(totHd / 1e6).toFixed(2)}M (decode ~${(totHd * 4 / 1e6).toFixed(0)}MB)`)
console.log(`hd.js: ${(fs.statSync(OUT).size / 1e6).toFixed(2)}MB`)
