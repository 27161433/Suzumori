# 唯一的反射是讀取小米自啟動狀態（platform/AutoStart.kt），對象是系統的 AppOpsManager，
# 不在 App 內，R8 不會改動它，不需要額外的保留規則。沒有使用序列化。
