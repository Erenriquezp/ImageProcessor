# Añade <activeCodePage>UTF-8</activeCodePage> al manifiesto incrustado del lanzador (RT_MANIFEST)
# con BeginUpdateResource/UpdateResource, sin herramientas externas. Sin esto, el lanzador de jpackage
# no arranca desde una ruta con caracteres fuera de la página ANSI (CJK, emoji): "could not find
# java.dll". Requiere Windows 10 1903 o posterior para tener efecto. Idempotente.
# Uso: pwsh -File tools/package/Set-Utf8Manifest.ps1 -Exe <ruta.exe>
param([Parameter(Mandatory)] [string] $Exe)
$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public static class ResManifest {
  delegate bool EnumNames(IntPtr h, IntPtr type, IntPtr name, IntPtr param);
  delegate bool EnumLangs(IntPtr h, IntPtr type, IntPtr name, ushort lang, IntPtr param);
  [DllImport("kernel32", SetLastError = true, CharSet = CharSet.Unicode)] static extern IntPtr LoadLibraryExW(string f, IntPtr h, uint flags);
  [DllImport("kernel32", SetLastError = true)] static extern bool FreeLibrary(IntPtr h);
  [DllImport("kernel32", SetLastError = true)] static extern bool EnumResourceNamesW(IntPtr h, IntPtr type, EnumNames cb, IntPtr p);
  [DllImport("kernel32", SetLastError = true)] static extern bool EnumResourceLanguagesW(IntPtr h, IntPtr type, IntPtr name, EnumLangs cb, IntPtr p);
  [DllImport("kernel32", SetLastError = true)] static extern IntPtr FindResourceExW(IntPtr h, IntPtr type, IntPtr name, ushort lang);
  [DllImport("kernel32", SetLastError = true)] static extern IntPtr LoadResource(IntPtr h, IntPtr r);
  [DllImport("kernel32", SetLastError = true)] static extern IntPtr LockResource(IntPtr r);
  [DllImport("kernel32", SetLastError = true)] static extern uint SizeofResource(IntPtr h, IntPtr r);
  [DllImport("kernel32", SetLastError = true, CharSet = CharSet.Unicode)] static extern IntPtr BeginUpdateResourceW(string f, bool del);
  [DllImport("kernel32", SetLastError = true)] static extern bool UpdateResourceW(IntPtr u, IntPtr type, IntPtr name, ushort lang, byte[] data, uint size);
  [DllImport("kernel32", SetLastError = true)] static extern bool EndUpdateResourceW(IntPtr u, bool discard);

  public static string Patch(string exe) {
    IntPtr RT_MANIFEST = (IntPtr)24;
    IntPtr h = LoadLibraryExW(exe, IntPtr.Zero, 0x2 /* LOAD_LIBRARY_AS_DATAFILE */);
    if (h == IntPtr.Zero) throw new Exception("LoadLibraryEx " + Marshal.GetLastWin32Error());
    var found = new List<Tuple<IntPtr, ushort>>();
    EnumResourceNamesW(h, RT_MANIFEST, (hh, t, n, p) => {
      EnumResourceLanguagesW(hh, t, n, (h2, t2, n2, lang, p2) => { found.Add(Tuple.Create(n2, lang)); return true; }, IntPtr.Zero);
      return true; }, IntPtr.Zero);
    if (found.Count != 1) { FreeLibrary(h); throw new Exception("manifiestos encontrados: " + found.Count); }
    IntPtr name = found[0].Item1; ushort lng = found[0].Item2;
    IntPtr r = FindResourceExW(h, RT_MANIFEST, name, lng);
    uint size = SizeofResource(h, r);
    byte[] data = new byte[size];
    Marshal.Copy(LockResource(LoadResource(h, r)), data, 0, (int)size);
    FreeLibrary(h);
    string xml = Encoding.UTF8.GetString(data);
    if (xml.Contains("activeCodePage")) return "ya tenia activeCodePage";
    string tag = "<activeCodePage xmlns=\"http://schemas.microsoft.com/SMI/2019/WindowsSettings\">UTF-8</activeCodePage>";
    int at = xml.IndexOf("</asmv3:windowsSettings>");
    if (at < 0) throw new Exception("sin windowsSettings");
    xml = xml.Insert(at, tag);
    byte[] outData = Encoding.UTF8.GetBytes(xml);
    IntPtr u = BeginUpdateResourceW(exe, false);
    if (u == IntPtr.Zero) throw new Exception("BeginUpdateResource " + Marshal.GetLastWin32Error());
    if (!UpdateResourceW(u, RT_MANIFEST, name, lng, outData, (uint)outData.Length)) throw new Exception("UpdateResource " + Marshal.GetLastWin32Error());
    if (!EndUpdateResourceW(u, false)) throw new Exception("EndUpdateResource " + Marshal.GetLastWin32Error());
    return "manifiesto " + name + "/" + lng + " actualizado (" + data.Length + " -> " + outData.Length + " bytes)";
  }
}
'@
$file = Get-Item -LiteralPath $Exe
# jpackage deja el ejecutable de solo lectura.
$file.Attributes = $file.Attributes -band (-bnot [IO.FileAttributes]::ReadOnly)
[ResManifest]::Patch($file.FullName)
