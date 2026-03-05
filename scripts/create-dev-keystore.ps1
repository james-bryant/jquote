param(
    [string]$KeystorePath = "$env:USERPROFILE\.jquote\jquote-keystore.p12",
    [string]$Password = "changeit",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool not found. Install a JDK and ensure keytool is on PATH."
}

if (Test-Path -LiteralPath $KeystorePath) {
    if (-not $Force) {
        Write-Host "Keystore already exists: $KeystorePath. Use -Force to overwrite."
        exit 1
    }
    Remove-Item -LiteralPath $KeystorePath -Force
}

$directory = Split-Path -Parent $KeystorePath
if ($directory -and -not (Test-Path -LiteralPath $directory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

$san = "SAN=DNS:localhost,IP:127.0.0.1"
$dname = "CN=127.0.0.1, OU=JQuote, O=JQuote, L=Local, S=Local, C=US"

& keytool -genkeypair -alias jquote -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 `
    -keystore $KeystorePath -storepass $Password -keypass $Password -dname $dname -ext $san

Write-Host "Created keystore at $KeystorePath"
Write-Host "Set JQUOTE_SSL_KEYSTORE and JQUOTE_SSL_KEYSTORE_PASSWORD if you changed the defaults."
