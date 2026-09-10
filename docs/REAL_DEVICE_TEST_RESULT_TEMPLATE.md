# SK Health Guardian — Real Device Test Result

## Build / cihaz bilgileri

- Tarih / saat:
- APK commit SHA:
- Mobile APK sürümü:
- Wear APK sürümü:
- Telefon modeli:
- Android sürümü:
- Watch modeli:
- Wear OS sürümü:
- PC-60FW model / firmware:
- Test yapan:

## Test sonucu

- Test ID:
- Test adı:
- Sonuç: PASS / FAIL / BLOCKED
- Başlangıç saati:
- Bitiş saati:

## Ön koşullar

- [ ] Gerekli izinler açık
- [ ] Bluetooth/Wear bağlantısı hazır
- [ ] Acil durum kişileri test numaraları ile ayarlı
- [ ] Threshold ve timeout değerleri kaydedildi
- [ ] Test öncesi timeline/log zamanı not edildi

## Uygulanan adımlar

1.
2.
3.
4.

## Beklenen davranış


## Gerçekleşen davranış


## Safety invariant kontrolü

- [ ] Aynı exact alarm duplicate remote delivery üretmedi
- [ ] ACK yalnız hedef exact alarmı etkiledi
- [ ] Başka aktif notification/escalation silinmedi
- [ ] Recovery sonrası yeni incident gerektiğinde yeniden alarm üretebildi
- [ ] Reboot/reconnect sonrası eski ACK edilmiş alarm yeniden doğmadı
- [ ] Kaynak önceliği beklenen cihazda kaldı

## Kanıtlar

- Timeline ekran görüntüsü:
- Telefon log dosyası:
- Watch log dosyası:
- SMS callback kaydı:
- Arama kaydı:
- Ek ekran görüntüleri / video:

## Hata varsa

- Tekrar üretilebilir: Evet / Hayır
- Tekrar üretme adımları:
- İlk gözlenen commit:
- Son sağlam commit:
- Kullanıcı etkisi:
- Safety-critical: Evet / Hayır
- Geçici workaround:

## Son karar

- [ ] PASS — release gate için kabul
- [ ] FAIL — düzeltme gerekli
- [ ] BLOCKED — donanım/izin/çevre engeli

Notlar:
