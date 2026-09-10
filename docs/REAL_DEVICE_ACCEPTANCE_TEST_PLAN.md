# SK Health Guardian — Real Device Acceptance Test Plan

Bu belge, otomatik/unit/stress/chaos testlerinden sonra Galaxy Watch + Android telefon + PC-60FW ile yapılacak fiziksel kabul testlerini tanımlar.

## Test ortamı

- Android telefon: uygulamanın en güncel debug/release candidate APK'sı
- Wear OS saat: Galaxy Watch uyumlu build
- Parmak oksimetre: PC-60FW
- En az iki test acil durum kişisi
- SMS ve arama izinleri açık
- Bluetooth/Wear bağlantısı açık
- Pil optimizasyonu test senaryosuna göre açık/kapalı denenmeli
- Test başlamadan önce uygulama timeline/log ekranı temiz ve saatler senkron olmalı

## Kabul kriteri

Her test PASS/FAIL olarak işaretlenir. FAIL olan safety-critical test çözülmeden release candidate kabul edilmez.

## A. Watch ölçüm ve alarm testleri

### A1 — Normal SpO₂
1. Saatten normal bir SpO₂ ölçümü al.
2. Telefon ekranında ölçümün geldiğini doğrula.
3. Timeline'da kaynak ve zaman bilgisini kontrol et.
4. SMS/arama alarmı oluşmamalı.

Beklenen: PASS = veri gelir, alarm yok.

### A2 — Kritik SpO₂ ilk ölçümde
Test/QA injection kullanılmalı; kullanıcıda gerçek hipoksemi oluşturulmaya çalışılmamalıdır.

1. SpO₂ < kritik eşik simüle et.
2. İlk geçerli reading'de alarmın oluştuğunu doğrula.
3. İkinci ölçüm beklenmemeli.
4. Telefon notification/full-screen alarm, SMS/arama ve escalation planı exact aynı alarm kimliğini taşımalı.

Beklenen: PASS = ilk reading'de tek exact alarm zinciri.

### A3 — Düşük ama kritik olmayan SpO₂
1. Değeri düşük eşik altı fakat kritik üstü simüle et.
2. İlk reading'de uzak alarm oluşmamalı.
3. Ayarlı confirm count kadar ardışık düşük reading gönder.
4. Confirm tamamlandığında tek alarm oluşmalı.

Beklenen: PASS = erken alarm yok, onay sonrası tek alarm.

### A4 — Düşük → normal → düşük reset
Beklenen: ilk düşük seri normal reading ile sıfırlanır; yeni seri yeniden confirm ister.

### A5 — Yüksek nabız
Ayarlı yüksek HR eşiği üstünde ardışık reading gönder.

Beklenen: confirm tamamlanana kadar alarm yok; sonra tek alarm ve recovery olmadan duplicate yok.

## B. PC-60FW testleri

### B1 — Eşleşme ve canlı veri
1. PC-60FW'yi eşleştir.
2. Parmakta stabil ölçüm al.
3. Telefonun SpO₂, nabız, paket zamanı ve cihaz durumunu göstermesini doğrula.

### B2 — Kaynak önceliği
1. Watch ve PC60 aynı anda veri üretsin.
2. PC60 fresh/valid iken SpO₂ alarm kararının PC60'a geçtiğini doğrula.
3. PC60 çıkarılınca/stale olunca Watch'un tekrar yetki aldığını doğrula.

### B3 — PC60 düşük SpO₂ recovery penceresi
QA/simülasyon kullanılmalı.

1. Parmak takılı/valid durumda SpO₂ eşik altına düşsün.
2. Yaklaşık 2 dakikalık recovery süresi dolmadan alarm oluşmamalı.
3. Ölçüm >85'e stabil dönerse alarm oluşmamalı.
4. Recovery gerçekleşmezse tek alarm oluşmalı.

### B4 — Probe-off / pulse-searching / invalid reset
Beklenen: geçersiz durum düşük episode'u sıfırlar; yanlış alarm üretmez.

### B5 — PC60 yüksek nabız
Beklenen: ayarlı confirm mantığıyla tek alarm; episode recovery olmadan duplicate yok.

## C. Bağlantı ve teknik alarm testleri

### C1 — Watch heartbeat kesilmesi
1. En az bir heartbeat aldıktan sonra Watch bağlantısını kes.
2. Ayarlı timeout dolmadan alarm olmamalı.
3. Timeout sonunda tek WATCH_DISCONNECTED alarmı oluşmalı.

### C2 — DATA_STALE + WATCH_DISCONNECTED coalescing
1. Watch bağlantısını kes ve veri akışını durdur.
2. Önce WATCH_DISCONNECTED oluşsun.
3. Daha sonra DATA_STALE koşulu oluşsun.

Beklenen: ikinci SMS/arama yok; timeline'da TEKNİK ALARM BİRLEŞTİRİLDİ.

### C3 — SENSOR_FAILURE + disconnect + stale üçlü coalescing
Beklenen: aynı kesintisiz teknik incident için yalnız ilk remote alarm; diğerleri timeline'a birleşmiş olarak düşer.

### C4 — Recovery sonrası yeni incident
1. Teknik alarm üret.
2. Sonra yeni geçerli reading al.
3. Tekrar bağlantı/veri kaybı oluştur.

Beklenen: ikinci olay yeni incident sayılır ve yeniden alarm verebilir.

## D. ACK ve escalation testleri

### D1 — Telefon ACK
1. Alarm üret.
2. Telefonda sustur.

Beklenen: ACK durable yazılır, yalnız o exact notification kapanır, yalnız o escalation iptal edilir.

### D2 — Watch ACK
1. Alarm üret.
2. Watch'tan sustur.
3. Telefon ACK'i diske yazdıktan sonra receipt dönmeli.
4. Watch pending ACK'i yalnız exact receipt sonrası silmeli.

### D3 — Receipt kaybı
1. ACK sırasında bağlantıyı keserek receipt'i kaybettir.
2. Watch pending ACK'i tutmalı.
3. Bağlantı dönünce ACK yeniden gönderilmeli.
4. Telefon duplicate ACK'te yalnız receipt tekrar göndermeli; timeline/cancel tekrar çalışmamalı.

### D4 — İki eşzamanlı alarm
1. Alarm A ve Alarm B'yi ayrı exact kimliklerle üret.
2. İki notification ve iki pending escalation olduğunu doğrula.
3. Yalnız Alarm A'yı ACK et.

Beklenen: A kapanır; B notification ve escalation aktif kalır.

## E. Reboot / process death testleri

### E1 — Telefon reboot + pending escalation
Beklenen: yaş sınırı içindeki unacknowledged exact escalation restore edilir; ACK edilmiş veya süresi dolmuş alarm restore edilmez.

### E2 — Watch reboot + active alarm episode
Beklenen: persisted AlarmEngine state restore olur ve recovery olmadan duplicate alarm üretmez.

### E3 — Watch offline technical queue
1. Telefon bağlantısını kes.
2. Watch'ta SENSOR_FAILURE üret.
3. Watch process/restart senaryosu uygula.
4. Bağlantıyı geri getir.

Beklenen: durable kuyruğa girmiş technical alert telefona ulaşır; recovery sonrası eski alarm replay edilmez.

## F. SMS / arama teslim zinciri

### F1 — SMS callback
Beklenen: sent/delivered callback exact messageId + exact alertId ile loglanır.

### F2 — SMS retry
1. İlk SMS gönderimini başarısız kıl.
2. ACK verme.

Beklenen: retry planlanır. ACK verilirse retry başlamadan önce iptal olur.

### F3 — Arama ACK yarışı
Alarm ACK'i arama başlamadan hemen önce ver.

Beklenen: CallPlacer final-boundary exact ACK kontrolü nedeniyle arama başlamaz.

## G. Pil / Doze / uzun süreli soak

### G1 — 8 saat Watch soak
- 5 dakikalık ölçüm periyodu
- heartbeat gözlemi
- duplicate alarm olmamalı
- beklenmeyen service death/restart olmamalı

### G2 — 8 saat PC60 intermittent soak
- cihazı farklı zamanlarda tak/çıkar
- invalid/probe-off davranışını gözle
- kaynak geçişlerinde duplicate alarm olmamalı

### G3 — Android Doze / ekran kapalı
- telefon ekranını uzun süre kapalı tut
- mümkünse Doze koşullarını zorla
- heartbeat, stale detection, AlarmManager escalation ve SMS/call davranışını doğrula

## H. Release gate

Release candidate ancak aşağıdakilerin tamamı sağlanırsa kabul edilir:

- A1–A5 PASS
- B1–B5 PASS
- C1–C4 PASS
- D1–D4 PASS
- E1–E3 PASS
- F1–F3 PASS
- G1–G3 PASS
- En az bir 8 saatlik gerçek cihaz soak testinde safety-critical hata yok
- Aynı physical incident için duplicate remote alarm yok
- ACK sonrası başka exact alarmın notification/escalation state'i bozulmuyor
- Reboot sonrası acknowledged alarm yeniden doğmuyor
- PC60 valid/fresh iken Watch yanlışlıkla SpO₂ authority almıyor

## Test kayıt formatı

Her test için şu kayıt tutulur:

- Test ID
- Tarih/saat
- Telefon modeli / Android sürümü
- Watch modeli / Wear OS sürümü
- APK commit SHA
- PC60 firmware/model bilgisi
- PASS / FAIL
- Timeline ekran görüntüsü
- İlgili log dosyası
- Varsa hata açıklaması ve tekrar üretme adımları
