# =====================================================================
#  deploy.ps1 — Build + deploiement du plugin PrivateMines
#  Usage : depuis n'importe ou, lance      .\deploy.ps1
#  (ou      powershell -File "C:\Users\kos\Desktop\PrivateMines-dev\deploy.ps1")
#
#  Comportement : compile avec Maven. Le jar n'est copie dans le serveur
#  QUE si le build reussit (BUILD SUCCESS). En cas d'echec, rien n'est
#  deploye et les erreurs sont affichees.
# =====================================================================

$ErrorActionPreference = "Stop"

$pom      = "C:\Users\kos\Desktop\PrivateMines-dev\pom.xml"
$jarSrc   = "C:\Users\kos\Desktop\PrivateMines-dev\target\PrivateMines.jar"
$jarDst   = "C:\Users\kos\Desktop\Serv prison\plugins\PrivateMines.jar"

Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Build du plugin PrivateMines..." -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""

# On lance Maven et on capture toute la sortie (pour la relire).
$output = & mvn -DskipTests -f $pom package 2>&1
$output | ForEach-Object { Write-Host $_ }

# Verdict : on cherche la ligne BUILD SUCCESS dans la sortie de Maven.
$success = $output | Select-String -Pattern "BUILD SUCCESS" -Quiet

Write-Host ""
if ($success) {
    Write-Host "==================================================" -ForegroundColor Green
    Write-Host "  BUILD SUCCESS -> deploiement du jar..." -ForegroundColor Green
    Write-Host "==================================================" -ForegroundColor Green
    try {
        Copy-Item $jarSrc $jarDst -Force
        Write-Host ""
        Write-Host "  OK : jar copie dans le serveur." -ForegroundColor Green
        Write-Host "  -> Ferme le serveur pour recharger le plugin." -ForegroundColor Yellow
        Write-Host ""
    } catch {
        Write-Host ""
        Write-Host "  ECHEC de la copie : $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "  (le serveur tourne peut-etre et verrouille le jar ?)" -ForegroundColor Red
        Write-Host ""
        exit 1
    }
} else {
    Write-Host "==================================================" -ForegroundColor Red
    Write-Host "  BUILD FAILURE -> AUCUN deploiement." -ForegroundColor Red
    Write-Host "  Corrige les erreurs ci-dessus puis relance." -ForegroundColor Red
    Write-Host "==================================================" -ForegroundColor Red
    Write-Host ""
    exit 1
}
