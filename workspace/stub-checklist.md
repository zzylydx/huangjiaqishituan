# 桩层实现清单（阶段1审计结论）

审计方法：javap -c -p 全部 12 个游戏类，提取所有外部方法/字段引用。
结论：**零反射、零 Class.forName、零 ClassLoader 直接使用**。资源全部走 `Class.getResourceAsStream`（绝对路径常量）。
java.* 面极小（ByteArray/Data 流、String/StringBuffer、Thread、Hashtable/Vector/Random、System.arraycopy/currentTimeMillis/gc/out、Math.abs/max、Integer.parseInt）——TeaVM classlib 全部覆盖。

## MIDP API 面（桩必须实现）

### javax.microedition.midlet
- MIDlet.<init> / getAppProperty / notifyDestroyed / platformRequest（→false，剔除计费）

### javax.microedition.lcdui
- Display.getDisplay / setCurrent
- Displayable.getWidth / getHeight / isShown / addCommand / setCommandListener
- Canvas: paint(抽象) + keyPressed/keyReleased/keyRepeated/pointerPressed/pointerReleased（被 FullCn 覆盖）+ setFullScreenMode + serviceRepaints + repaint
- Graphics: setColor/translate/setClip/clipRect/getClipX,Y,W,H/fillRect/drawRect/drawLine/fillArc/drawImage/drawString/getFont/setFont
- Image.createImage / getWidth / getHeight
- Font.getFont / getHeight / stringWidth
- GameCanvas.<init>(boolean) / getGameAction / getKeyCode / getKeyName / getKeyStates(未用，仍实现)
- Form.<init> / append / get；StringItem.<init> / getText；Command.<init>；Item.setDefaultCommand / setItemCommandListener
  （以上 Form/StringItem/Command 仅被 e 类计费界面使用——stub 成可渲染空壳即可，甚至不需要真 UI）

### javax.microedition.io
- Connector.open（"sms://" 返回哑连接） / Connection.close

### javax.wireless.messaging（全部哑桩，计费终结点）
- MessageConnection.newMessage / send / close
- Message.setAddress；TextMessage.setPayloadText

### javax.microedition.media
- Manager.createPlayer(InputStream, "audio/midi")；Player.setLoopCount / start / stop

### javax.microedition.rms
- RecordStore.openRecordStore / getRecord / setRecord / addRecord / getNumRecords / closeRecordStore / deleteRecordStore
- （+ 异常体系 RecordStoreException/RecordStoreNotFoundException/RecordStoreFullException/InvalidRecordIDException）

## 资源清单（ResourceSupplier 需内联）
128X116logo-2.png, 240X320logo.png, 240_320.pak, 32_32.png, a~j.class 之外的全部资源：
bg.mid, main.mid, HaKuNa.png, lang.dat, mm.png, QQBig.png, setting.dat, setting.dat_11, icons/*.png
（注意：游戏代码经 Class.getResourceAsStream("/名字") 读取，绝对路径）

## 关键键码面（注入用）
-1 UP, -2 DOWN, -3 LEFT, -4 RIGHT, -5 FIRE, -6 LSK, -7 RSK, '0'-'9'=48-57, '*'=42, '#'=35
FullCn.keyCodeConvert: -30→-6, -31→-7, -23→42（仅转换这三个，其余透传）
