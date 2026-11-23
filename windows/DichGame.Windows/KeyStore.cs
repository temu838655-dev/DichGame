using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace DichGame.Windows;

internal static class KeyStore
{
    private static readonly string StoreFile = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "DichGame", "gemini.keys");

    public static void Save(string keys)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(StoreFile)!);
        byte[] bytes = ProtectedData.Protect(Encoding.UTF8.GetBytes(keys), null, DataProtectionScope.CurrentUser);
        File.WriteAllBytes(StoreFile, bytes);
    }

    public static string Load()
    {
        try
        {
            if (!File.Exists(StoreFile)) return "";
            byte[] bytes = ProtectedData.Unprotect(File.ReadAllBytes(StoreFile), null, DataProtectionScope.CurrentUser);
            return Encoding.UTF8.GetString(bytes);
        }
        catch (CryptographicException) { return ""; }
        catch (IOException) { return ""; }
    }
}
