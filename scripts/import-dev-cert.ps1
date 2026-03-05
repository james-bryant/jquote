param(
    [string]$KeystorePath = "$env:USERPROFILE\.jquote\jquote-keystore.p12",
    [string]$Password = "changeit",
    [string]$CertPath = "$env:TEMP\jquote-localhost.cer",
    [string]$CertStoreLocation = "Cert:\CurrentUser\Root",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool not found. Install a JDK and ensure keytool is on PATH."
}

if (-not (Test-Path -LiteralPath $KeystorePath)) {
    throw "Keystore not found at $KeystorePath. Run scripts/create-dev-keystore.ps1 first."
}

if (Test-Path -LiteralPath $CertPath) {
    if (-not $Force) {
        Write-Host "Cert file already exists: $CertPath. Use -Force to overwrite."
        exit 1
    }
    Remove-Item -LiteralPath $CertPath -Force
}

& keytool -exportcert -alias jquote -keystore $KeystorePath -storepass $Password -rfc -file $CertPath

Import-Certificate -FilePath $CertPath -CertStoreLocation $CertStoreLocation | Out-Null

Write-Host "Imported certificate into $CertStoreLocation from $CertPath"
