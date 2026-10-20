# Centaurus BLE Test 0.3.1

Experimental stand-alone Android BLE communication checker for Lost Vape BT200. Created by reading publicly exposed communication structures in the user's installed official Centaurus 1.0.9 APK.

**Read-only device diagnostic**: discovers BLE devices, connects GATT, detects service FFA0, enables notification CCCD, sends only the official AA550100007C14 status query and checks response AA55 / CRC16 Modbus. Does not write settings or invoke heating, OTA, updates, firmware or external networking.

Open Android app > grant Nearby Devices permission > Scan > select BT200 > wait up to 10 seconds > Export diagnostic if no valid response.

Independent/unofficial, not affiliated with Lost Vape. No hardware verification has yet been performed. This build intentionally prioritizes a minimal installable APK with no third-party UI dependencies.
