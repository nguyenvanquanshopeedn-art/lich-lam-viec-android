# HƯỚNG DẪN BUILD FILE APK - LỊCH LÀM VIỆC & KẾ HOẠCH

Gói mã nguồn này đã cấu hình sẵn dự án Android hoàn chỉnh (`Kotlin` + `Jetpack Compose` + `AlarmManager`).

## Cách 1: Xuất file APK trên Android Studio (Máy tính)
1. Giải nén thư mục dự án `LichLamViec_Android_Project`.
2. Mở **Android Studio** → chọn **Open** → trỏ tới thư mục vừa giải nén.
3. Chờ Gradle đồng bộ xong (nếu hiện thông báo thiếu `gradle-wrapper.jar`, bấm **Fix / Sync Project with Gradle Files** hoặc tạo wrapper nhanh bằng lệnh `gradle wrapper` trong Terminal của Android Studio).
4. Trên thanh menu trên cùng chọn:
   **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. Khi hoàn tất, bấm **locate** ở thông báo góc dưới phải để lấy file `app-debug.apk`, chuyển sang điện thoại Android và cài đặt.

## Cách 2: Xuất file APK trực tiếp trên điện thoại Android (Không cần máy tính)
1. Cài ứng dụng **AndroidIDE** hoặc **AIDE** trên điện thoại Android.
2. Giải nén thư mục dự án vào bộ nhớ điện thoại.
3. Mở dự án trong ứng dụng và bấm **Build / Run** để xuất và cài đặt file `.apk` trực tiếp.
