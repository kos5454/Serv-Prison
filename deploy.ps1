# =====================================================================
#  deploy.ps1 — Build + deploiement du plugin PrivateMines
#  Usage : depuis n'importe ou, lance      .\deploy.ps1
#
#  Comportement : compile avec Maven. Le jar n'est copie dans le serveur
#  QUE si le build reussit (BUILD SUCCESS). En cas d'echec, rien n'est
#  deploye et les erreurs sont affichees.
#
#  Les chemins se deduisent de l'emplacement de CE fichier, donc le script
#  marche quel que soit l'endroit ou le projet est clone. Seule la ligne
#  $serveur ci-dessous est a adapter : c'est le dossier du serveur Minecraft.
#  Elle peut aussi etre passee en parametre :   .\deploy.ps1 -serveur "D:\mon serveur"
# =====================================================================

param(
    [string]$serveur = (Join-Path (Split-Path -Parent $PSScriptRoot) "Serv prison")
)

$ErrorActionPreference = "Stop"

$racine   = $PSScriptRoot
$pom      = Join-Path $racine "pom.xml"
$jarSrc   = Join-Path $racine "target\PrivateMines.jar"
$jarDst   = Join-Path $serveur "plugins\PrivateMines.jar"

# Si le dossier du serveur n'existe pas, autant le dire tout de suite plutot
# que de compiler pendant une minute pour echouer a la copie.
if (-not (Test-Path $serveur)) {
    Write-Host ""
    Write-Host "  Dossier serveur introuvable : $serveur" -ForegroundColor Red
    Write-Host "  Corrige la valeur par defaut de -serveur en tete de ce script," -ForegroundColor Red
    Write-Host "  ou lance : .\deploy.ps1 -serveur `"chemin\vers\le\serveur`"" -ForegroundColor Red
    Write-Host ""
    exit 1
}

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
