using Microsoft.Win32;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Speech.Synthesis;
using System.Windows;
using System.Windows.Media.Imaging;
using DrawingBitmap = System.Drawing.Bitmap;
using Forms = System.Windows.Forms;

namespace DichGame.Windows;

public partial class MainWindow : Window
{
    private byte[]? _image;
    private readonly SpeechSynthesizer _voice = new();

    public MainWindow()
    {
        InitializeComponent();
        Keys.Password = KeyStore.Load();
    }

    private void SaveKeys_Click(object sender, RoutedEventArgs e)
    {
        try { KeyStore.Save(Keys.Password); Status.Text = "Đã lưu khóa Gemini mã hóa trên máy Windows."; }
        catch (Exception ex) { Status.Text = "Không lưu được khóa: " + ex.Message; }
    }

    private void OpenImage_Click(object sender, RoutedEventArgs e)
    {
        var picker = new Microsoft.Win32.OpenFileDialog
        {
            Filter = "Ảnh game|*.png;*.jpg;*.jpeg;*.bmp;*.webp|Tất cả tệp|*.*",
            Title = "Chọn ảnh chụp game"
        };
        if (picker.ShowDialog() != true) return;
        try
        {
            using var bitmap = new DrawingBitmap(picker.FileName);
            SetImage(bitmap, "Ảnh: " + Path.GetFileName(picker.FileName));
        }
        catch (Exception ex) { Status.Text = "Không mở được ảnh: " + ex.Message; }
    }

    private async void Capture_Click(object sender, RoutedEventArgs e)
    {
        // Chỉ chụp màn hình khi người dùng bấm; ẩn giao diện để không chụp lộ khóa Gemini.
        Hide();
        try
        {
            await Task.Delay(500);
            var screen = Forms.Screen.PrimaryScreen
                ?? throw new InvalidOperationException("Không tìm được màn hình.");
            using var bitmap = new DrawingBitmap(screen.Bounds.Width, screen.Bounds.Height);
            using (var gfx = Graphics.FromImage(bitmap))
                gfx.CopyFromScreen(screen.Bounds.Location, System.Drawing.Point.Empty, screen.Bounds.Size);
            SetImage(bitmap, "Ảnh chụp màn hình chính");
        }
        catch (Exception ex) { Status.Text = "Không thể chụp màn hình: " + ex.Message; }
        finally { Show(); Activate(); }
    }

    private void SetImage(DrawingBitmap bitmap, string caption)
    {
        double ratio = Math.Min(1.0, 1600.0 / Math.Max(bitmap.Width, bitmap.Height));
        int w = Math.Max(1, (int)(bitmap.Width * ratio));
        int h = Math.Max(1, (int)(bitmap.Height * ratio));
        using var scaled = new DrawingBitmap(w, h);
        using (var g = Graphics.FromImage(scaled))
        {
            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
            g.DrawImage(bitmap, 0, 0, w, h);
        }
        using var ms = new MemoryStream();
        scaled.Save(ms, ImageFormat.Jpeg);
        _image = ms.ToArray();
        var preview = new BitmapImage();
        preview.BeginInit();
        preview.CacheOption = BitmapCacheOption.OnLoad;
        preview.StreamSource = new MemoryStream(_image);
        preview.EndInit();
        preview.Freeze();
        ScreenshotPreview.Source = preview;
        ImagePlaceholder.Visibility = Visibility.Collapsed;
        PhotoNote.Text = caption;
        Status.Text = "Đã có ảnh, bấm Dịch sang tiếng Việt.";
    }

    private async void Translate_Click(object sender, RoutedEventArgs e)
    {
        if (_image is null) { Status.Text = "Chọn hoặc chụp ảnh game trước."; return; }
        TranslateButton.IsEnabled = false;
        Status.Text = "Đang đọc và dịch chữ từ ảnh bằng Gemini...";
        try
        {
            var lang = (LanguageBox.SelectedItem as System.Windows.Controls.ComboBoxItem)?.Content?.ToString() ?? "Tự nhận diện";
            Output.Text = await GeminiClient.TranslateAsync(_image, GameName.Text, lang, Keys.Password);
            Status.Text = "Hoàn tất. Bản dịch chỉ phản ánh chữ Gemini nhận dạng được từ ảnh.";
        }
        catch (Exception ex) { Status.Text = "Lỗi dịch: " + ex.Message; }
        finally { TranslateButton.IsEnabled = true; }
    }

    private void Copy_Click(object sender, RoutedEventArgs e)
    {
        try { System.Windows.Clipboard.SetText(Output.Text); Status.Text = "Đã sao chép bản dịch."; }
        catch (Exception ex) { Status.Text = ex.Message; }
    }

    private void Speak_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var vietnamese = _voice.GetInstalledVoices().FirstOrDefault(v =>
                v.Enabled && v.VoiceInfo.Culture.Name.Equals("vi-VN", StringComparison.OrdinalIgnoreCase));
            if (vietnamese is null)
            {
                Status.Text = "Windows chưa có giọng tiếng Việt. Hãy cài giọng vi-VN trong Windows Settings.";
                return;
            }
            _voice.SpeakAsyncCancelAll();
            _voice.SelectVoice(vietnamese.VoiceInfo.Name);
            _voice.SpeakAsync(Output.Text);
            Status.Text = "Đang đọc bằng giọng Việt của Windows.";
        }
        catch (Exception ex) { Status.Text = "Không thể đọc tiếng Việt: " + ex.Message; }
    }

    protected override void OnClosed(EventArgs e)
    {
        _voice.Dispose();
        base.OnClosed(e);
    }
}
