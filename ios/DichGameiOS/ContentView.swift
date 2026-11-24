import SwiftUI
import PhotosUI
import AVFoundation
import UIKit

private enum Palette {
    static let bg = Color(red: 0.047, green: 0.055, blue: 0.073)
    static let card = Color(red: 0.095, green: 0.103, blue: 0.128)
    static let gold = Color(red: 0.99, green: 0.76, blue: 0.43)
    static let goldDeep = Color(red: 0.77, green: 0.49, blue: 0.23)
    static let muted = Color(red: 0.63, green: 0.64, blue: 0.69)
    static let cream = Color(red: 0.98, green: 0.96, blue: 0.92)
    static let line = Color.white.opacity(0.095)
    static let gradient = LinearGradient(
        colors: [gold, goldDeep], startPoint: .topLeading, endPoint: .bottomTrailing)
}

struct ContentView: View {
    @AppStorage("dichgame.selectedGame") private var game = "Grand Theft Auto VI"
    @AppStorage("dichgame.sourceLanguage") private var originalLanguage = "Tự nhận diện"
    @State private var keys = SecureKeys.load()
    @State private var selectedItem: PhotosPickerItem?
    @State private var screenshot: UIImage?
    @State private var translation = ""
    @State private var busy = false
    @State private var error = ""
    @State private var savedKeys = false
    @State private var showKeys = false
    @State private var speaker = AVSpeechSynthesizer()
    @AppStorage("dichgame.tv.ip") private var tvIP = ""
    @AppStorage("dichgame.tv.pairedIP") private var tvPairedIP = ""
    @AppStorage("dichgame.tv.token") private var tvToken = ""
    @AppStorage("dichgame.tv.size") private var tvSize = 36.0
    @AppStorage("dichgame.tv.speak") private var tvSpeak = false
    @State private var tvCode = ""
    @State private var tvBusy = false
    @State private var tvStatus = ""

    var body: some View {
        NavigationStack {
            ZStack {
                Palette.bg.ignoresSafeArea()
                VStack {
                    HStack {
                        Spacer()
                        Circle().fill(Palette.gold.opacity(0.12))
                            .frame(width: 280, height: 280)
                            .blur(radius: 92)
                            .offset(x: 120, y: -150)
                    }
                    Spacer()
                }.ignoresSafeArea().allowsHitTesting(false)

                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        header
                        hero
                        contextPanel
                        imagePanel
                        resultPanel
                        tvPanel
                        settingsPanel
                        footer
                    }
                    .frame(maxWidth: 690)
                    .padding(.horizontal, 18)
                    .padding(.top, 22)
                    .padding(.bottom, 38)
                    .frame(maxWidth: .infinity)
                }
                .scrollIndicators(.hidden)
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        .preferredColorScheme(.dark)
    }

    private var header: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 15).fill(Palette.gradient)
                Image(systemName: "gamecontroller.fill")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(Color.black.opacity(0.83))
            }
            .frame(width: 51, height: 51)
            VStack(alignment: .leading, spacing: 3) {
                Text("DICHGAME").font(.system(size: 18, weight: .black, design: .rounded))
                    .tracking(2.3).foregroundStyle(Palette.cream)
                Text("CONSOLE TRANSLATOR")
                    .font(.system(size: 10, weight: .bold, design: .rounded))
                    .tracking(2.1).foregroundStyle(Palette.gold)
            }
            Spacer()
            Image(systemName: "sparkles")
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(Palette.gold)
                .padding(13)
                .background(Palette.card, in: Circle())
                .overlay(Circle().stroke(Palette.line, lineWidth: 1))
                .accessibilityLabel("Giao diện DichGame")
        }
    }

    private var hero: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 27)
                .fill(LinearGradient(colors: [
                    Color(red: 0.21, green: 0.18, blue: 0.16),
                    Palette.card,
                    Palette.bg
                ], startPoint: .topLeading, endPoint: .bottomTrailing))
            Circle().stroke(Palette.gold.opacity(0.17), lineWidth: 1)
                .frame(width: 255, height: 255)
                .offset(x: 215, y: -115)
            Circle().stroke(Palette.gold.opacity(0.14), lineWidth: 1)
                .frame(width: 190, height: 190)
                .offset(x: 245, y: -84)
            VStack(alignment: .leading, spacing: 15) {
                HStack(spacing: 8) {
                    Circle().fill(Color.green).frame(width: 7, height: 7)
                    Text("BẢN DỊCH GAME AI")
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .tracking(2)
                        .foregroundStyle(Palette.gold)
                }
                Text("Mọi thế giới game.\nMột ngôn ngữ của bạn.")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .tracking(-0.9)
                    .foregroundStyle(Palette.cream)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Chọn ảnh màn hình game, Gemini đọc và chuyển lời thoại sang tiếng Việt.")
                    .font(.system(size: 13))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 8) {
                    miniPill("GEMINI AI", icon: "sparkles")
                    miniPill("VIETNAMESE", icon: "character.bubble")
                }
            }
            .padding(24)
        }
        .frame(maxWidth: .infinity, minHeight: 218)
        .clipShape(RoundedRectangle(cornerRadius: 27))
        .overlay(RoundedRectangle(cornerRadius: 27)
            .stroke(Palette.gold.opacity(0.26), lineWidth: 1))
    }

    private func miniPill(_ title: String, icon: String) -> some View {
        Label(title, systemImage: icon)
            .font(.system(size: 9, weight: .bold))
            .foregroundStyle(Palette.gold)
            .padding(.horizontal, 10).padding(.vertical, 8)
            .background(Palette.gold.opacity(0.08), in: Capsule())
            .overlay(Capsule().stroke(Palette.gold.opacity(0.21), lineWidth: 1))
    }

    private var contextPanel: some View {
        panel(title: "Hồ sơ game", subtitle: "Ngữ cảnh để dịch đúng cách gọi", icon: "gamecontroller") {
            VStack(alignment: .leading, spacing: 9) {
                smallLabel("TÊN GAME")
                TextField("Tên game đang chơi", text: $game)
                    .textInputAutocapitalization(.words)
                    .inputStyle()
                smallLabel("NGÔN NGỮ GỐC")
                HStack {
                    Picker("Ngôn ngữ gốc", selection: $originalLanguage) {
                        ForEach(["Tự nhận diện", "Tiếng Anh", "Tiếng Nhật",
                                 "Tiếng Trung", "Tiếng Hàn"], id: \.self) { Text($0) }
                    }
                    .tint(Palette.gold)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Spacer()
                    Image(systemName: "globe").foregroundStyle(Palette.gold.opacity(0.8))
                }
                .padding(.horizontal, 10)
                .background(Color.white.opacity(0.035), in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private var imagePanel: some View {
        panel(title: "Nguồn hình ảnh", subtitle: "Chọn ảnh chụp màn hình từ thư viện", icon: "photo.on.rectangle.angled") {
            Group {
                if let screenshot {
                    Image(uiImage: screenshot)
                        .resizable().scaledToFit().frame(maxWidth: .infinity)
                        .frame(maxHeight: 268)
                } else {
                    VStack(spacing: 12) {
                        Image(systemName: "photo.badge.plus")
                            .font(.system(size: 34, weight: .ultraLight))
                            .foregroundStyle(Palette.gold)
                        Text("Chưa có ảnh game").font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Palette.cream)
                        Text("Ảnh chỉ được gửi cho Gemini khi bạn bấm Dịch")
                            .font(.system(size: 11)).foregroundStyle(Palette.muted)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 175)
                }
            }
            .frame(maxWidth: .infinity)
            .background(Color.black.opacity(0.23), in: RoundedRectangle(cornerRadius: 16))
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Palette.line, style: StrokeStyle(lineWidth: 1, dash: [5, 5])))

            PhotosPicker(selection: $selectedItem, matching: .images) {
                Label(screenshot == nil ? "Chọn ảnh game" : "Đổi ảnh khác", systemImage: "photo.on.rectangle")
                    .frame(maxWidth: .infinity).frame(height: 44)
            }
            .buttonStyle(.plain).foregroundStyle(Palette.cream)
            .background(Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 13))
            .overlay(RoundedRectangle(cornerRadius: 13).stroke(Palette.line))
            .onChange(of: selectedItem) { _, item in
                Task {
                    guard let item, let data = try? await item.loadTransferable(type: Data.self),
                          let image = UIImage(data: data) else { return }
                    screenshot = image
                    translation = ""
                    error = ""
                }
            }

            Button(action: translate) {
                HStack(spacing: 10) {
                    if busy { ProgressView().tint(.black) }
                    else { Image(systemName: "sparkles.rectangle.stack.fill") }
                    Text(busy ? "Đang phân tích hình..." : "DỊCH SANG TIẾNG VIỆT")
                        .tracking(1.1)
                }
                .font(.system(size: 12, weight: .heavy, design: .rounded))
                .frame(maxWidth: .infinity).frame(height: 51)
            }
            .buttonStyle(.plain)
            .foregroundStyle(.black)
            .background(Palette.gradient, in: RoundedRectangle(cornerRadius: 13))
            .disabled(busy || screenshot == nil || keys.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .opacity(busy || screenshot == nil || keys.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.54 : 1)
        }
    }

    private var resultPanel: some View {
        panel(title: "Phụ đề tiếng Việt", subtitle: "Kết quả sau khi AI đọc ảnh", icon: "captions.bubble.fill") {
            Text(translation.isEmpty ? "Bản dịch của bạn sẽ xuất hiện tại đây..." : translation)
                .font(.system(size: 15, weight: .medium, design: .rounded))
                .foregroundStyle(translation.isEmpty ? Palette.muted : Palette.cream)
                .frame(maxWidth: .infinity, minHeight: 84, alignment: .topLeading)
                .textSelection(.enabled)
                .padding(14)
                .background(Color.black.opacity(0.22), in: RoundedRectangle(cornerRadius: 13))
            if !translation.isEmpty {
                HStack(spacing: 9) {
                    Button {
                        UIPasteboard.general.string = translation
                    } label: {
                        Label("Sao chép", systemImage: "doc.on.doc")
                    }
                    Button {
                        speaker.stopSpeaking(at: .immediate)
                        let utterance = AVSpeechUtterance(string: translation)
                        utterance.voice = AVSpeechSynthesisVoice(language: "vi-VN")
                        utterance.rate = 0.48
                        speaker.speak(utterance)
                    } label: {
                        Label("Nghe đọc", systemImage: "speaker.wave.2")
                    }
                }
                .font(.system(size: 12, weight: .semibold))
                .tint(Palette.gold)
            }
            if !error.isEmpty {
                Label(error, systemImage: "exclamationmark.triangle")
                    .font(.system(size: 12)).foregroundStyle(.red)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var tvPanel: some View {
        panel(title: "Phụ đề trên Android TV", subtitle: "Gửi bản dịch ảnh qua Wi-Fi nội bộ", icon: "tv") {
            Text("Mở app DichGame TV, cấp quyền hiển thị và nhập mã 8 số. Đây chưa phải dịch trực tiếp khi chơi game trên iOS.")
                .font(.system(size: 12)).foregroundStyle(Palette.muted)
            TextField("IP của TV", text: $tvIP).keyboardType(.decimalPad).autocorrectionDisabled().inputStyle()
            TextField("Mã 8 số trên TV", text: $tvCode).keyboardType(.numberPad).inputStyle()
            Button("Ghép TV") {
                let ip = tvIP.trimmingCharacters(in: .whitespacesAndNewlines), code = tvCode
                tvBusy = true
                Task {
                    defer { tvBusy = false }
                    do {
                        tvToken = try await TVLink.pair(ip: ip, code: code)
                        tvPairedIP = ip
                        tvCode = ""
                        tvStatus = "Đã ghép TV"
                    } catch { tvStatus = error.localizedDescription }
                }
            }.disabled(tvBusy || !TVLink.privateIPv4(tvIP.trimmingCharacters(in: .whitespacesAndNewlines)))
            Text("Cỡ chữ: \(Int(tvSize))").font(.system(size: 13))
            Slider(value: $tvSize, in: 14...80, step: 1).tint(Palette.gold)
            Toggle("Đọc bằng loa TV", isOn: $tvSpeak).tint(Palette.gold)
            HStack {
                Button("Gửi bản dịch") { sendToTV(translation) }.disabled(tvBusy || translation.isEmpty || !tvReady)
                Button("Thử TV") { sendToTV("DichGame: phụ đề thử nghiệm") }.disabled(tvBusy || !tvReady)
                Button("Xóa chữ") { sendToTV("", clear: true) }.disabled(tvBusy || !tvReady)
            }.font(.system(size: 13))
            if !tvToken.isEmpty {
                Button("Quên TV") { tvToken = ""; tvPairedIP = ""; tvStatus = "Đã quên TV trên iPhone" }.disabled(tvBusy)
            }
            Text(tvStatus.isEmpty ? (tvReady ? "Đã lưu kết nối TV" : "Chưa ghép TV") : tvStatus)
                .font(.system(size: 12)).foregroundStyle(Palette.muted)
        }.tint(Palette.gold)
    }

    private var tvReady: Bool {
        tvIP.trimmingCharacters(in: .whitespacesAndNewlines) == tvPairedIP && TVLink.validToken(tvToken)
    }

    private func sendToTV(_ text: String, clear: Bool = false) {
        guard tvReady else { return }
        let ip = tvPairedIP, token = tvToken, size = Int(tvSize), speak = tvSpeak
        tvBusy = true
        Task {
            defer { tvBusy = false }
            do {
                try await TVLink.send(ip: ip, token: token, text: text, size: size, speak: speak, clear: clear)
                tvStatus = "TV đã nhận lệnh"
            } catch { tvStatus = error.localizedDescription }
        }
    }

    private var settingsPanel: some View {
        panel(title: "Kết nối Gemini", subtitle: "Khóa chỉ lưu trong Keychain của iPhone", icon: "lock.shield.fill") {
            HStack {
                Group {
                    if showKeys {
                        TextField("Khóa Gemini, cách nhau bằng dấu phẩy", text: $keys)
                    } else {
                        SecureField("Nhập khóa Gemini", text: $keys)
                    }
                }
                .textInputAutocapitalization(.never).autocorrectionDisabled()
                .font(.system(size: 13))
                Button {
                    showKeys.toggle()
                } label: {
                    Image(systemName: showKeys ? "eye.slash" : "eye")
                        .foregroundStyle(Palette.gold)
                }
                .accessibilityLabel(showKeys ? "Ẩn khóa" : "Hiện khóa")
            }
            .padding(.horizontal, 12).frame(height: 48)
            .background(Color.white.opacity(0.05), in: RoundedRectangle(cornerRadius: 12))
            Button {
                SecureKeys.save(keys)
                savedKeys = true
            } label: {
                Label(savedKeys ? "Đã lưu trong Keychain" : "Lưu khóa an toàn",
                      systemImage: savedKeys ? "checkmark.shield" : "key.fill")
                    .font(.system(size: 13, weight: .semibold))
            }
            .foregroundStyle(Palette.gold)
        }
    }

    private var footer: some View {
        VStack(spacing: 7) {
            Text("DICHGAME  /  iOS PREVIEW")
                .font(.system(size: 10, weight: .bold)).tracking(2.5)
                .foregroundStyle(Palette.gold.opacity(0.72))
            Text("Bản thử nghiệm: dịch ảnh do bạn chọn. Chưa hỗ trợ phủ phụ đề lên ứng dụng khác hoặc Remote Play.")
                .font(.system(size: 11)).foregroundStyle(Palette.muted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(.top, 8)
    }

    private func translate() {
        guard let screenshot else { return }
        busy = true
        error = ""
        Task {
            defer { busy = false }
            do {
                translation = try await GameImageTranslator.translate(
                    image: screenshot, game: game,
                    language: originalLanguage, rawKeys: keys)
            } catch {
                self.error = error.localizedDescription
            }
        }
    }

    private func smallLabel(_ text: String) -> some View {
        Text(text).font(.system(size: 10, weight: .bold))
            .tracking(1.7).foregroundStyle(Palette.muted)
    }

    private func panel<Content: View>(
        title: String, subtitle: String, icon: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 15) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 19))
                    .foregroundStyle(Palette.gold)
                    .frame(width: 42, height: 42)
                    .background(Palette.gold.opacity(0.09), in: RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(Palette.cream)
                    Text(subtitle).font(.system(size: 11)).foregroundStyle(Palette.muted)
                }
            }
            content()
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22)
            .strokeBorder(LinearGradient(colors: [Palette.gold.opacity(0.20), Palette.line],
                                         startPoint: .topLeading, endPoint: .bottomTrailing),
                          lineWidth: 1))
    }
}

private struct PremiumInput: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(.system(size: 15))
            .foregroundStyle(Palette.cream)
            .padding(.horizontal, 12)
            .frame(height: 47)
            .background(Color.white.opacity(0.045),
                        in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12)
                .stroke(Palette.line, lineWidth: 1))
    }
}

private extension View {
    func inputStyle() -> some View { modifier(PremiumInput()) }
}
