import SwiftUI
import PhotosUI
import AVFoundation

private enum Theme {
    static let gold = Color(red: 1, green: 0.71, blue: 0.34)
    static let bg = Color(red: 0.055, green: 0.057, blue: 0.075)
    static let panel = Color(red: 0.13, green: 0.13, blue: 0.15)
}

struct ContentView: View {
    @State private var game = "Grand Theft Auto VI"
    @State private var originalLanguage = "Tiếng Anh"
    @State private var keys = SecureKeys.load()
    @State private var selectedItem: PhotosPickerItem?
    @State private var screenshot: UIImage?
    @State private var translation = ""
    @State private var busy = false
    @State private var error = ""
    private let speaker = AVSpeechSynthesizer()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("CONSOLE TRANSLATOR").font(.system(size: 24, weight: .heavy))
                        Text("Chơi game nào cũng hiểu").foregroundStyle(Theme.gold)
                    }
                    .padding(.vertical, 8)
                    panel {
                        Label("Kho game · ngữ cảnh dịch", systemImage: "gamecontroller.fill")
                            .font(.headline).foregroundStyle(Theme.gold)
                        TextField("Tên game", text: $game)
                            .textFieldStyle(.roundedBorder)
                        Picker("Ngôn ngữ gốc", selection: $originalLanguage) {
                            ForEach(["Tiếng Anh", "Tiếng Nhật", "Tiếng Trung", "Tiếng Hàn",
                                     "Tự nhận diện"], id: \.self) { Text($0) }
                        }
                        .tint(Theme.gold)
                    }

                    panel {
                        Label("Ảnh chụp game", systemImage: "photo.on.rectangle.angled")
                            .font(.headline).foregroundStyle(Theme.gold)
                        if let screenshot {
                            Image(uiImage: screenshot)
                                .resizable().scaledToFit()
                                .frame(maxHeight: 270).clipShape(RoundedRectangle(cornerRadius: 12))
                        } else {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.black.opacity(0.25))
                                .frame(height: 145)
                                .overlay {
                                    VStack(spacing: 9) {
                                        Image(systemName: "photo.badge.plus").font(.largeTitle)
                                        Text("Chọn ảnh màn hình game").font(.subheadline)
                                    }.foregroundStyle(.secondary)
                                }
                        }
                        PhotosPicker(selection: $selectedItem, matching: .images) {
                            Label("Chọn ảnh từ thư viện", systemImage: "photo")
                                .frame(maxWidth: .infinity).padding(13)
                        }
                        .background(Theme.gold, in: RoundedRectangle(cornerRadius: 13))
                        .foregroundStyle(.black)
                        .fontWeight(.bold)
                        .onChange(of: selectedItem) { _, item in
                            Task {
                                guard let item, let data = try? await item.loadTransferable(type: Data.self),
                                      let image = UIImage(data: data) else { return }
                                screenshot = image
                                translation = ""
                            }
                        }
                        Button {
                            Task {
                                guard let screenshot else { return }
                                busy = true
                                error = ""
                                defer { busy = false }
                                do {
                                    translation = try await GameImageTranslator.translate(
                                        image: screenshot,
                                        game: game,
                                        language: originalLanguage,
                                        rawKeys: keys)
                                } catch {
                                    self.error = error.localizedDescription
                                }
                            }
                        } label: {
                            HStack {
                                if busy { ProgressView().tint(.black) }
                                Text(busy ? "Đang dịch..." : "Dịch ảnh sang tiếng Việt")
                            }.frame(maxWidth: .infinity).padding(14)
                        }
                        .buttonStyle(.borderedProminent).tint(Theme.gold)
                        .disabled(busy || screenshot == nil || keys.trimmingCharacters(in: .whitespaces).isEmpty)
                    }

                    panel {
                        Label("Kết quả tiếng Việt", systemImage: "text.bubble")
                            .font(.headline).foregroundStyle(Theme.gold)
                        Text(translation.isEmpty ? "Bản dịch sẽ hiện ở đây." : translation)
                            .foregroundStyle(translation.isEmpty ? .secondary : .primary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .textSelection(.enabled)
                        if !translation.isEmpty {
                            Button {
                                speaker.stopSpeaking(at: .immediate)
                                let utterance = AVSpeechUtterance(string: translation)
                                utterance.voice = AVSpeechSynthesisVoice(language: "vi-VN")
                                utterance.rate = 0.48
                                speaker.speak(utterance)
                            } label: {
                                Label("Đọc tiếng Việt", systemImage: "speaker.wave.2.fill")
                            }.tint(Theme.gold)
                        }
                        if !error.isEmpty {
                            Text(error).foregroundStyle(.red).font(.footnote)
                        }
                    }

                    panel {
                        Label("Khóa Gemini", systemImage: "key.fill")
                            .font(.headline).foregroundStyle(Theme.gold)
                        SecureField("Khóa 1, Khóa 2, ...", text: $keys)
                            .textFieldStyle(.roundedBorder)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        Button("Lưu khóa vào Keychain của iPhone") { SecureKeys.save(keys) }
                            .font(.subheadline).foregroundStyle(Theme.gold)
                        Text("Tự thử khóa kế tiếp khi khóa trước bị 401, 403 hoặc 429. Không ghi khóa vào mã nguồn.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Text("Bản thử nghiệm iOS: dịch ảnh chụp do bạn chọn. Chưa hỗ trợ phụ đề nổi trên game khác hoặc Remote Play tích hợp.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                .padding(18)
            }
            .background(Theme.bg.ignoresSafeArea())
            .navigationBarHidden(true)
        }
    }

    private func panel<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 13, content: content)
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.panel, in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20)
                .strokeBorder(Theme.gold.opacity(0.25), lineWidth: 1))
    }
}
