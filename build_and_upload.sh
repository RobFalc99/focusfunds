#!/usr/bin/env bash
# build_and_upload.sh
# Script di automazione per FocusFunds: Clean, Build Release APK, Git Commit, Push, Tag, GitHub Release Upload

# Colori per output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
DARKGRAY='\033[1;30m'
NC='\033[0m' # No Color

# Configurazione repository GitHub
GITHUB_TOKEN="${GITHUB_TOKEN:-}"
GITHUB_OWNER="RobFalc99"
GITHUB_REPO="focusfunds"

# Trova la directory dello script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR" || exit 1

# Carica GITHUB_TOKEN da .env locale se presente
if [ -f "$SCRIPT_DIR/.env" ]; then
    if [ -z "$GITHUB_TOKEN" ]; then
        local_token=$(grep -E "^GITHUB_TOKEN=" "$SCRIPT_DIR/.env" | cut -d'=' -f2- | tr -d '"' | tr -d "'")
        if [ -n "$local_token" ]; then
            GITHUB_TOKEN="$local_token"
        fi
    fi
fi

echo -e "${CYAN}==========================================${NC}"
echo -e "${CYAN}   FocusFunds Automation Pipeline (Linux)${NC}"
echo -e "${CYAN}==========================================${NC}"
echo -e "${DARKGRAY}Working directory: $(pwd)${NC}"

# --- STEP 1: Git Inizializzazione se necessario ---
if [ ! -d ".git" ]; then
    echo -e "\n${YELLOW}Inizializzazione repository Git...${NC}"
    git init
    git checkout -b main
    git remote add origin "https://${GITHUB_TOKEN}@github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git"
    echo -e "${GREEN}[OK] Repository Git inizializzato con successo!${NC}"
fi

# --- STEP 2: Build Release APK ---
echo -e "\n${YELLOW}[1/3] Compilazione Release APK... [BUILD]${NC}"
echo -e "${DARKGRAY}       (Questo potrebbe richiedere alcuni minuti...)${NC}"

# Pulizia build precedenti
echo -e "${DARKGRAY}Esecuzione gradlew clean...${NC}"
./gradlew clean --quiet

# Compilazione APK Release
./gradlew assembleRelease
if [ $? -ne 0 ]; then
    echo -e "${RED}[ERRORE] La compilazione dell'APK tramite Gradle è fallita!${NC}"
    exit 1
fi

apk_path="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$apk_path" ]; then
    echo -e "${RED}[ERRORE] File APK non trovato a: $apk_path${NC}"
    exit 1
fi

apk_size=$(du -h "$apk_path" | cut -f1)
echo -e "${GREEN}[OK] APK compilato con successo! ($apk_size)${NC}"

# Copia in path temporaneo
temp_apk="/tmp/FocusFunds_upload_temp.apk"
cp "$apk_path" "$temp_apk"
echo -e "${DARKGRAY}APK copiato in path temporaneo: $temp_apk${NC}"

# --- STEP 3: Commit, Tag e Push ---
echo -e "\n${YELLOW}[2/3] Sincronizzazione codice su GitHub... [GIT]${NC}"

timestamp=$(date +'%Y%m%dd-%H%M%S')
version="v$(date +'%Y.%m.%d')-$(date +'%H%M%S')"
release_name="FocusFunds $version"
file_name="FocusFunds-$timestamp.apk"

# Configurazione utente git locale se non impostata globalmente
git config --local user.email "tony@example.com"
git config --local user.name "Tony"

echo -e "${WHITE}Commit e push del codice...${NC}"
git add -A
commit_msg="build: release $version"

if [ -n "$(git status --porcelain)" ]; then
    git commit -m "$commit_msg"
fi

git push -u origin main --force
echo -e "${GREEN}[OK] Codice caricato su GitHub!${NC}"

# Crea e carica il tag
git tag "$version"
git push origin "$version"
echo -e "${GREEN}[OK] Tag $version creato e caricato!${NC}"

# --- STEP 4: GitHub Release & Upload ---
echo -e "\n${YELLOW}[3/3] Creazione Release su GitHub... [UPLOAD]${NC}"

# Crea la Release su GitHub via API
release_json=$(cat <<EOF
{
  "tag_name": "$version",
  "name": "$release_name",
  "body": "FocusFunds Mobile Application Release. Compilazione automatica del $(date +'%d/%m/%Y %H:%M'). APK size: $apk_size.",
  "draft": false,
  "prerelease": false
}
EOF
)

response=$(curl -s -X POST \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  -H "Content-Type: application/json" \
  -d "$release_json" \
  "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases")

release_url=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('html_url', ''))" 2>/dev/null)
upload_url=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('upload_url', '').split('{')[0])" 2>/dev/null)

if [ -z "$upload_url" ]; then
    echo -e "${RED}[ERRORE] Creazione release fallita!${NC}"
    echo -e "${RED}$response${NC}"
    rm -f "$temp_apk"
    exit 1
fi

echo -e "${GREEN}[OK] Release creata su GitHub: $release_url${NC}"

# Upload dell'APK
echo -e "${WHITE}Upload dell'APK in corso...${NC}"
upload_response=$(curl -s -X POST \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"$temp_apk" \
  "$upload_url?name=$file_name")

download_url=$(echo "$upload_response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('browser_download_url', ''))" 2>/dev/null)

if [ -z "$download_url" ]; then
    echo -e "${RED}[ERRORE] Caricamento file APK fallito!${NC}"
    echo -e "${RED}$upload_response${NC}"
    rm -f "$temp_apk"
    exit 1
fi

echo -e "${GREEN}[OK] APK caricato con successo!${NC}"

# Genera QR Code
escaped_url=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$download_url'))" 2>/dev/null || echo "$download_url")
qr_url="https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=$escaped_url"

echo -e "\n${GREEN}=======================================================${NC}"
echo -e "${GREEN}    COMPILAZIONE E DISTRIBUZIONE COMPLETATE!           ${NC}"
echo -e "${GREEN}=======================================================${NC}"
echo -e ""
echo -e "  Release:  ${WHITE}$release_url${NC}"
echo -e "  Download: ${CYAN}$download_url${NC}"
echo -e ""
echo -e "  QR Code (apri nel browser o scansiona): "
echo -e "  ${YELLOW}$qr_url${NC}"
echo -e "${GREEN}=======================================================${NC}"

# Invio notifica tramite script centrale Telegram
/home/tony/scripts/send_notification.sh "Compilato FocusFunds APK Release, sincronizzato codice git e creata release GitHub" "- APK download: <a href=\"$download_url\">$file_name</a>\n- Release GitHub: <a href=\"$release_url\">$release_name</a>"

# Cleanup
rm -f "$temp_apk"
